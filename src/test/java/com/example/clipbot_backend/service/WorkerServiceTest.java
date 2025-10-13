package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.DetectionParams;
import com.example.clipbot_backend.dto.RenderOptions;
import com.example.clipbot_backend.dto.RenderResult;
import com.example.clipbot_backend.dto.SegmentDTO;
import com.example.clipbot_backend.dto.SubtitleFiles;
import com.example.clipbot_backend.engine.Interfaces.ClipRenderEngine;
import com.example.clipbot_backend.engine.Interfaces.DetectionEngine;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.model.*;
import com.example.clipbot_backend.repository.AssetRepository;
import com.example.clipbot_backend.repository.ClipRepository;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.SegmentRepository;
import com.example.clipbot_backend.repository.TranscriptRepository;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.example.clipbot_backend.service.Interfaces.SubtitleService;
import com.example.clipbot_backend.util.AssetKind;
import com.example.clipbot_backend.util.ClipStatus;
import com.example.clipbot_backend.util.JobType;
import com.example.clipbot_backend.util.MediaStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkerServiceTest {

    @Mock JobService jobService;
    @Mock TranscriptService transcriptService;
    @Mock MediaRepository mediaRepo;
    @Mock TranscriptRepository transcriptRepo;
    @Mock SegmentRepository segmentRepo;
    @Mock ClipRepository clipRepo;
    @Mock AssetRepository assetRepo;
    @Mock TranscriptionEngine transcriptionEngine;
    @Mock DetectionEngine detectionEngine;
    @Mock ClipRenderEngine clipRenderEngine;
    @Mock StorageService storageService;
    @Mock SubtitleService subtitleService;

    @InjectMocks WorkerService workerService;


    @Test
    void handleTranscribeShouldTranscribeAndEnqueueDetect() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();

        Media media = new Media();
        media.setId(mediaId);
        media.setObjectKey("object-key");
        media.setStatus(MediaStatus.UPLOADED);

        when(mediaRepo.findById(mediaId)).thenReturn(Optional.of(media));

        var words = List.of(new TranscriptionEngine.Word(0, 1_000, "Hello"));
        var result = new TranscriptionEngine.Result(
                "Hello world",
                words,
                "en",
                "gpt4",
                Map.of("foo", "bar")
        );
        when(transcriptionEngine.transcribe(any())).thenReturn(result);

        Job job = spy(new Job(JobType.TRANSCRIBE));
        job.setMedia(media);
        doReturn(jobId).when(job).getId(); // <-- geen setId nodig

        workerService.handleTranscribe(job);

        ArgumentCaptor<TranscriptionEngine.Request> reqCap = ArgumentCaptor.forClass(TranscriptionEngine.Request.class);
        verify(transcriptionEngine).transcribe(reqCap.capture());
        var req = reqCap.getValue();
        assertEquals(mediaId, req.mediaId());
        assertEquals("object-key", req.objectKey());
        assertNull(req.langHint());

        verify(transcriptService).upsert(mediaId, result);

        assertEquals(MediaStatus.PROCESSING, media.getStatus());
        verify(mediaRepo).save(media);

        verify(jobService).markDone(eq(jobId), eq(Map.of(
                "transcriptLang", "en",
                "provider", "gpt4"
        )));
        verify(jobService).enqueue(eq(mediaId), eq(JobType.DETECT),
                argThat(m -> "en".equals(m.get("lang")) && "gpt4".equals(m.get("provider"))));

        verifyNoInteractions(detectionEngine, segmentRepo, clipRepo, assetRepo, clipRenderEngine, storageService, subtitleService);
    }

    @Test
    void handleDetectWithoutTranscriptShouldMarkErrorAndRequeueTranscribe() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();

        Media media = new Media();
        media.setId(mediaId);
        media.setObjectKey("object-key");

        when(mediaRepo.findById(mediaId)).thenReturn(Optional.of(media));
        when(transcriptRepo.findTopByMediaOrderByCreatedAtDesc(media)).thenReturn(Optional.empty());

        Job job = spy(new Job(JobType.DETECT));
        job.setMedia(media);
        job.setPayload(new HashMap<>());
        doReturn(jobId).when(job).getId();

        workerService.handleDetect(job);

        verify(jobService).markError(eq(jobId), eq("No transcript"), eq(Map.of()));
        verify(jobService).enqueue(mediaId, JobType.TRANSCRIBE, Map.of());

        verifyNoInteractions(detectionEngine);
        verify(segmentRepo, never()).saveAll(any());
    }

    @Test
    void handleDetectWithTranscriptShouldRunDetectionAndPersistSegments() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();

        Media media = new Media();
        media.setId(mediaId);
        media.setObjectKey("object-key");

        Transcript transcript = new Transcript(media, "en", "gpt4");

        when(segmentRepo.saveAll(any(Iterable.class))).thenAnswer(inv -> inv.getArgument(0));

        when(mediaRepo.findById(mediaId)).thenReturn(Optional.of(media));
        when(transcriptRepo.findByMediaAndLangAndProvider(media, "en", "gpt4")).thenReturn(Optional.of(transcript));

        Job job = spy(new Job(JobType.DETECT));
        job.setMedia(media);
        job.setPayload(new HashMap<>(Map.of(
                "lang", "en",
                "provider", "gpt4",
                "sceneThreshold", 0.75
        )));
        doReturn(jobId).when(job).getId();

        Path rawPath = Path.of("/tmp/media.mp4");
        when(storageService.resolveRaw("object-key")).thenReturn(rawPath);

        var segments = List.of(new SegmentDTO(0, 1_000, BigDecimal.valueOf(0.9), Map.of("rank", 1)));
        when(detectionEngine.detect(eq(rawPath), eq(transcript), any(DetectionParams.class))).thenReturn(segments);

        workerService.handleDetect(job);

        ArgumentCaptor<DetectionParams> paramsCap = ArgumentCaptor.forClass(DetectionParams.class);
        verify(detectionEngine).detect(eq(rawPath), eq(transcript), paramsCap.capture());
        var params = paramsCap.getValue();
        assertEquals(0.75, params.sceneThreshold(), 1e-6);
        assertEquals(8, params.maxCandidates());

        verify(segmentRepo).deleteByMedia(media);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Segment>> savedCap = ArgumentCaptor.forClass(Iterable.class);
        verify(segmentRepo).saveAll(savedCap.capture());

        List<Segment> savedList = new ArrayList<>();
        savedCap.getValue().forEach(savedList::add);
        assertEquals(1, savedList.size());

        Segment saved = savedList.get(0);
        assertEquals(media, saved.getMedia());
        assertEquals(0, saved.getStartMs());
        assertEquals(1_000, saved.getEndMs());
        assertEquals(BigDecimal.valueOf(0.9), saved.getScore());
        assertEquals(Map.of("rank", 1), saved.getMeta());

        verify(jobService).markDone(eq(jobId), eq(Map.of("segmentCount", 1)));
        verify(jobService, never()).markError(any(), any(), any());
    }

    @Test
    void handleClipWithTranscriptShouldRenderAndPersistAssets() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID clipId = UUID.randomUUID();

        Account owner = new Account("acc-1", "Owner");

        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setOwner(owner);
        media.setObjectKey("media-key");

        Clip clip = new Clip(media, 1_000, 5_000);
        clip.setStatus(ClipStatus.RENDERING);
        clip.setMeta(Map.of("layout", "vertical"));

        Transcript transcript = new Transcript(media, "en", "openai");

        Job job = spy(new Job(JobType.CLIP));
        job.setPayload(new HashMap<>(Map.of("clipId", clipId.toString())));
        doReturn(jobId).when(job).getId();

        Path rawPath = Path.of("/tmp/media-key.mp4");

        when(clipRepo.findById(clipId)).thenReturn(Optional.of(clip));
        when(transcriptRepo.findByMediaAndLangAndProvider(media, "en", "openai")).thenReturn(Optional.of(transcript));
        when(storageService.resolveRaw("media-key")).thenReturn(rawPath);
        when(clipRepo.save(any(Clip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assetRepo.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));


        SubtitleFiles subtitleFiles = new SubtitleFiles("clip.srt", 321L, "clip.vtt", 654L);
        when(subtitleService.buildSubtitles(transcript, clip.getStartMs(), clip.getEndMs())).thenReturn(subtitleFiles);

        RenderResult renderResult = new RenderResult("clip.mp4", 2_000L, "thumb.png", 400L);
        when(clipRenderEngine.render(eq(rawPath), eq(clip.getStartMs()), eq(clip.getEndMs()), any(RenderOptions.class)))
                .thenReturn(renderResult);

        workerService.handleClip(job);

        ArgumentCaptor<RenderOptions> optionsCap = ArgumentCaptor.forClass(RenderOptions.class);
        verify(clipRenderEngine).render(eq(rawPath), eq(clip.getStartMs()), eq(clip.getEndMs()), optionsCap.capture());
        var options = optionsCap.getValue();
        assertEquals(clip.getMeta(), options.meta());
        assertSame(subtitleFiles, options.subtitles());

        ArgumentCaptor<Asset> assetCap = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepo, atLeast(1)).save(assetCap.capture());
        Map<AssetKind, Asset> byKind = assetCap.getAllValues().stream()
                .collect(Collectors.toMap(Asset::getKind, a -> a, (a, b) -> a));

        assertEquals("clip.mp4",  byKind.get(AssetKind.CLIP_MP4).getObjectKey());
        assertEquals(2000L,       byKind.get(AssetKind.CLIP_MP4).getSizeBytes());
        assertEquals("thumb.png", byKind.get(AssetKind.THUMBNAIL).getObjectKey());
        assertEquals(400L,        byKind.get(AssetKind.THUMBNAIL).getSizeBytes());
        assertEquals("clip.srt",  byKind.get(AssetKind.SUB_SRT).getObjectKey());
        assertEquals(321L,        byKind.get(AssetKind.SUB_SRT).getSizeBytes());
        assertEquals("clip.vtt",  byKind.get(AssetKind.SUB_VTT).getObjectKey());
        assertEquals(654L,        byKind.get(AssetKind.SUB_VTT).getSizeBytes());

        assertEquals("clip.srt", clip.getCaptionSrtKey());
        assertEquals("clip.vtt", clip.getCaptionVttKey());
        assertEquals(ClipStatus.READY, clip.getStatus());
        verify(clipRepo).save(clip);

        verify(jobService).markDone(eq(jobId), eq(Map.of("mp4Key", "clip.mp4")));
    }

    @Test
    void handleClipWithoutTranscriptShouldSkipSubtitles() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID clipId = UUID.randomUUID();

        Account owner = new Account("acc-2", "Owner");
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setOwner(owner);
        media.setObjectKey("media-key");

        Clip clip = new Clip(media, 2_000, 7_000);
        clip.setStatus(ClipStatus.RENDERING);
        clip.setMeta(Map.of());

        Job job = spy(new Job(JobType.CLIP));
        job.setPayload(new HashMap<>(Map.of("clipId", clipId.toString())));
        doReturn(jobId).when(job).getId();

        Path rawPath = Path.of("/tmp/media-key.mp4");
        when(clipRepo.findById(clipId)).thenReturn(Optional.of(clip));
        when(transcriptRepo.findByMediaAndLangAndProvider(media, "en", "openai")).thenReturn(Optional.empty());
        when(storageService.resolveRaw("media-key")).thenReturn(rawPath);
        when(clipRepo.save(any(Clip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assetRepo.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));


        RenderResult renderResult = new RenderResult("clip.mp4", 5_000L, "thumb.png", 900L);
        when(clipRenderEngine.render(eq(rawPath), eq(clip.getStartMs()), eq(clip.getEndMs()), any(RenderOptions.class)))
                .thenReturn(renderResult);

        workerService.handleClip(job);

        ArgumentCaptor<RenderOptions> optionsCap = ArgumentCaptor.forClass(RenderOptions.class);
        verify(clipRenderEngine).render(eq(rawPath), eq(clip.getStartMs()), eq(clip.getEndMs()), optionsCap.capture());
        var options = optionsCap.getValue();
        assertEquals(clip.getMeta(), options.meta());
        assertNull(options.subtitles());
        verify(subtitleService, never()).buildSubtitles(any(), anyLong(), anyLong());

        ArgumentCaptor<Asset> assetCap = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepo, atLeast(1)).save(assetCap.capture());
        Map<AssetKind, Asset> byKind = assetCap.getAllValues().stream()
                .collect(Collectors.toMap(Asset::getKind, a -> a, (a, b) -> a));

        assertTrue(byKind.containsKey(AssetKind.CLIP_MP4));
        assertEquals(5000L, byKind.get(AssetKind.CLIP_MP4).getSizeBytes());
        assertTrue(byKind.containsKey(AssetKind.THUMBNAIL));
        assertEquals(900L, byKind.get(AssetKind.THUMBNAIL).getSizeBytes());

        assertNull(clip.getCaptionSrtKey());
        assertNull(clip.getCaptionVttKey());
        assertEquals(ClipStatus.READY, clip.getStatus());
        verify(clipRepo).save(clip);

        verify(jobService).markDone(eq(jobId), eq(Map.of("mp4Key", "clip.mp4")));
    }

    @Test
    void handleClipWithMissingArtifactsShouldOnlyPersistMp4() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID clipId = UUID.randomUUID();

        Account owner = new Account("acc-3", "Owner");
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setOwner(owner);
        media.setObjectKey("media-key");

        Clip clip = new Clip(media, 3_000, 8_000);
        clip.setStatus(ClipStatus.RENDERING);
        clip.setMeta(Map.of("theme", "minimal"));

        Transcript transcript = new Transcript(media, "en", "openai");

        Job job = spy(new Job(JobType.CLIP));
        job.setPayload(new HashMap<>(Map.of("clipId", clipId.toString())));
        doReturn(jobId).when(job).getId();

        Path rawPath = Path.of("/tmp/media-key.mp4");
        when(clipRepo.findById(clipId)).thenReturn(Optional.of(clip));
        when(transcriptRepo.findByMediaAndLangAndProvider(media, "en", "openai")).thenReturn(Optional.of(transcript));

        SubtitleFiles subtitleFiles = new SubtitleFiles(null, 0L, null, 0L);
        when(subtitleService.buildSubtitles(transcript, clip.getStartMs(), clip.getEndMs())).thenReturn(subtitleFiles);

        when(storageService.resolveRaw("media-key")).thenReturn(rawPath);
        RenderResult renderResult = new RenderResult("clip.mp4", 4_200L, null, 0L);
        when(clipRenderEngine.render(eq(rawPath), eq(clip.getStartMs()), eq(clip.getEndMs()), any(RenderOptions.class)))
                .thenReturn(renderResult);
        when(clipRepo.save(any(Clip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assetRepo.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));

        workerService.handleClip(job);

        ArgumentCaptor<RenderOptions> optionsCap = ArgumentCaptor.forClass(RenderOptions.class);
        verify(clipRenderEngine).render(eq(rawPath), eq(clip.getStartMs()), eq(clip.getEndMs()), optionsCap.capture());
        var options = optionsCap.getValue();
        assertEquals(clip.getMeta(), options.meta());
        assertSame(subtitleFiles, options.subtitles());

        ArgumentCaptor<Asset> assetCap = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepo, atLeast(1)).save(assetCap.capture());
        Map<AssetKind, Asset> byKind = assetCap.getAllValues().stream()
                .collect(Collectors.toMap(Asset::getKind, a -> a, (a, b) -> a));

        assertTrue(byKind.containsKey(AssetKind.CLIP_MP4));
        assertEquals("clip.mp4", byKind.get(AssetKind.CLIP_MP4).getObjectKey());
        assertEquals(4200L, byKind.get(AssetKind.CLIP_MP4).getSizeBytes());

        assertFalse(byKind.containsKey(AssetKind.SUB_SRT));
        assertFalse(byKind.containsKey(AssetKind.SUB_VTT));
        assertFalse(byKind.containsKey(AssetKind.THUMBNAIL));

        assertNull(clip.getCaptionSrtKey());
        assertNull(clip.getCaptionVttKey());
        assertEquals(ClipStatus.READY, clip.getStatus());
        verify(clipRepo).save(clip);

        verify(jobService).markDone(eq(jobId), eq(Map.of("mp4Key", "clip.mp4")));
    }
}
