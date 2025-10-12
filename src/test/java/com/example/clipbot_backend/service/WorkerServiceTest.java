package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.DetectionParams;
import com.example.clipbot_backend.dto.RenderOptions;
import com.example.clipbot_backend.dto.RenderResult;
import com.example.clipbot_backend.dto.SegmentDTO;
import com.example.clipbot_backend.dto.SubtitleFiles;
import com.example.clipbot_backend.engine.Interfaces.ClipRenderEngine;
import com.example.clipbot_backend.engine.Interfaces.DetectionEngine;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Asset;
import com.example.clipbot_backend.model.Job;
import com.example.clipbot_backend.model.Clip;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Segment;
import com.example.clipbot_backend.model.Transcript;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkerServiceTest {

    @Mock
    private JobService jobService;
    @Mock
    private TranscriptService transcriptService;
    @Mock
    private MediaRepository mediaRepo;
    @Mock
    private TranscriptRepository transcriptRepo;
    @Mock
    private SegmentRepository segmentRepo;
    @Mock
    private ClipRepository clipRepo;
    @Mock
    private AssetRepository assetRepo;
    @Mock
    private TranscriptionEngine transcriptionEngine;
    @Mock
    private DetectionEngine detectionEngine;
    @Mock
    private ClipRenderEngine clipRenderEngine;
    @Mock
    private StorageService storageService;
    @Mock
    private SubtitleService subtitleService;

    @InjectMocks
    private WorkerService workerService;

    @Test
    void handleTranscribeShouldTranscribeAndEnqueueDetect() throws Exception {
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

        Job job = new Job(JobType.TRANSCRIBE);
        job.setMedia(media);

        workerService.handleTranscribe(job);

        ArgumentCaptor<TranscriptionEngine.Request> requestCaptor = ArgumentCaptor.forClass(TranscriptionEngine.Request.class);
        verify(transcriptionEngine).transcribe(requestCaptor.capture());
        TranscriptionEngine.Request request = requestCaptor.getValue();
        assertEquals(mediaId, request.mediaId());
        assertEquals("object-key", request.objectKey());
        assertNull(request.langHint());

        verify(transcriptService).upsert(mediaId, result);
        assertEquals(MediaStatus.PROCESSING, media.getStatus());
        verify(mediaRepo).save(media);

        verify(jobService).markDone(isNull(), eq(Map.of(
                "transcriptLang", "en",
                "provider", "gpt4"
        )));
        verify(jobService).enqueue(eq(mediaId), eq(JobType.DETECT), eq(Map.of(
                "lang", "en",
                "provider", "gpt4"
        )));

        verifyNoInteractions(detectionEngine, segmentRepo, clipRepo, assetRepo, clipRenderEngine, storageService, subtitleService);
    }

    @Test
    void handleDetectWithoutTranscriptShouldMarkErrorAndRequeueTranscribe() throws Exception {
        UUID mediaId = UUID.randomUUID();
        Media media = new Media();
        media.setId(mediaId);
        media.setObjectKey("object-key");
        when(mediaRepo.findById(mediaId)).thenReturn(Optional.of(media));
        when(transcriptRepo.findTopByMediaOrderByCreatedAtDesc(media)).thenReturn(Optional.empty());

        Job job = new Job(JobType.DETECT);
        job.setMedia(media);
        job.setPayload(new HashMap<>());

        workerService.handleDetect(job);

        verify(jobService).markError(isNull(), eq("No transcript"), eq(Map.of()));
        verify(jobService).enqueue(mediaId, JobType.TRANSCRIBE, Map.of());
        verifyNoInteractions(detectionEngine);
        verify(segmentRepo, never()).saveAll(any());
    }

    @Test
    void handleDetectWithTranscriptShouldRunDetectionAndPersistSegments() throws Exception {
        UUID mediaId = UUID.randomUUID();
        Media media = new Media();
        media.setId(mediaId);
        media.setObjectKey("object-key");

        Transcript transcript = new Transcript(media, "en", "gpt4");

        when(mediaRepo.findById(mediaId)).thenReturn(Optional.of(media));
        when(transcriptRepo.findByMediaAndLangAndProvider(media, "en", "gpt4")).thenReturn(Optional.of(transcript));

        Job job = new Job(JobType.DETECT);
        job.setMedia(media);
        job.setPayload(new HashMap<>(Map.of(
                "lang", "en",
                "provider", "gpt4",
                "sceneThreshold", "0.75"
        )));

        Path rawPath = Path.of("/tmp/media.mp4");
        when(storageService.resolveRaw("object-key")).thenReturn(rawPath);

        var segments = List.of(new SegmentDTO(0, 1_000, BigDecimal.valueOf(0.9), Map.of("rank", 1)));
        when(detectionEngine.detect(eq(rawPath), eq(transcript), any(DetectionParams.class))).thenReturn(segments);

        workerService.handleDetect(job);

        ArgumentCaptor<DetectionParams> paramsCaptor = ArgumentCaptor.forClass(DetectionParams.class);
        verify(detectionEngine).detect(eq(rawPath), eq(transcript), paramsCaptor.capture());
        DetectionParams params = paramsCaptor.getValue();
        assertEquals(0.75, params.sceneThreshold(), 1e-6);
        assertEquals(8, params.maxCandidates());

        verify(segmentRepo).deleteByMedia(media);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Segment>> savedSegmentsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(segmentRepo).saveAll(savedSegmentsCaptor.capture());
        List<Segment> savedSegments = new ArrayList<>();
        savedSegmentsCaptor.getValue().forEach(savedSegments::add);
        assertEquals(1, savedSegments.size());
        Segment saved = savedSegments.get(0);
        assertEquals(media, saved.getMedia());
        assertEquals(0, saved.getStartMs());
        assertEquals(1_000, saved.getEndMs());
        assertEquals(BigDecimal.valueOf(0.9), saved.getScore());
        assertEquals(Map.of("rank", 1), saved.getMeta());

        verify(jobService).markDone(isNull(), Map.of("segmentCount", 1));
        verify(jobService, never()).markError(any(), any(), any());
    }

    @Test
    void handleClipWithTranscriptShouldRenderAndPersistAssets() throws Exception {
        UUID clipId = UUID.randomUUID();
        Account owner = new Account("acc-1", "Owner");
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setOwner(owner);
        media.setObjectKey("media-key");

        Clip clip = new Clip(media, 1_000, 5_000);
        clip.setStatus(ClipStatus.RENDERING);
        clip.setMeta(Map.of("layout", "vertical"));

        Transcript transcript = new Transcript(media, "en", "whisper");

        Job job = new Job(JobType.CLIP);
        job.setPayload(new HashMap<>(Map.of("clipId", clipId.toString())));

        Path rawPath = Path.of("/tmp/media-key.mp4");
        when(clipRepo.findById(clipId)).thenReturn(Optional.of(clip));
        when(transcriptRepo.findByMediaAndLangAndProvider(media, "en", "whisper")).thenReturn(Optional.of(transcript));
        SubtitleFiles subtitleFiles = new SubtitleFiles("clip.srt", 321L, "clip.vtt", 654L);
        when(subtitleService.buildSubtitles(transcript, clip.getStartMs(), clip.getEndMs())).thenReturn(subtitleFiles);
        when(storageService.resolveRaw("media-key")).thenReturn(rawPath);
        RenderResult renderResult = new RenderResult("clip.mp4", 2_000L, "thumb.png", 400L);
        when(clipRenderEngine.render(eq(rawPath), eq(clip.getStartMs()), eq(clip.getEndMs()), any(RenderOptions.class)))
                .thenReturn(renderResult);

        workerService.handleClip(job);

        ArgumentCaptor<RenderOptions> optionsCaptor = ArgumentCaptor.forClass(RenderOptions.class);
        verify(clipRenderEngine).render(eq(rawPath), eq(clip.getStartMs()), eq(clip.getEndMs()), optionsCaptor.capture());
        RenderOptions options = optionsCaptor.getValue();
        assertEquals(clip.getMeta(), options.meta());
        assertSame(subtitleFiles, options.subtitles());

        ArgumentCaptor<Asset> assetCaptor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepo, times(4)).save(assetCaptor.capture());
        List<Asset> savedAssets = assetCaptor.getAllValues();
        assertEquals(4, savedAssets.size());
        Asset mp4Asset = savedAssets.get(0);
        assertEquals(AssetKind.CLIP_MP4, mp4Asset.getKind());
        assertEquals("clip.mp4", mp4Asset.getObjectKey());
        assertEquals(2_000L, mp4Asset.getSizeBytes());
        Asset thumbAsset = savedAssets.get(1);
        assertEquals(AssetKind.THUMBNAIL, thumbAsset.getKind());
        assertEquals("thumb.png", thumbAsset.getObjectKey());
        assertEquals(400L, thumbAsset.getSizeBytes());
        Asset srtAsset = savedAssets.get(2);
        assertEquals(AssetKind.SUB_SRT, srtAsset.getKind());
        assertEquals("clip.srt", srtAsset.getObjectKey());
        assertEquals(321L, srtAsset.getSizeBytes());
        Asset vttAsset = savedAssets.get(3);
        assertEquals(AssetKind.SUB_VTT, vttAsset.getKind());
        assertEquals("clip.vtt", vttAsset.getObjectKey());
        assertEquals(654L, vttAsset.getSizeBytes());

        assertEquals("clip.srt", clip.getCaptionSrtKey());
        assertEquals("clip.vtt", clip.getCaptionVttKey());
        assertEquals(ClipStatus.READY, clip.getStatus());
        verify(clipRepo).save(clip);
        verify(jobService).markDone(isNull(), Map.of("mp4Key", "clip.mp4"));
    }

    @Test
    void handleClipWithoutTranscriptShouldSkipSubtitles() throws Exception {
        UUID clipId = UUID.randomUUID();
        Account owner = new Account("acc-2", "Owner");
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setOwner(owner);
        media.setObjectKey("media-key");

        Clip clip = new Clip(media, 2_000, 7_000);
        clip.setStatus(ClipStatus.RENDERING);
        clip.setMeta(Map.of());

        Job job = new Job(JobType.CLIP);
        job.setPayload(new HashMap<>(Map.of("clipId", clipId.toString())));

        Path rawPath = Path.of("/tmp/media-key.mp4");
        when(clipRepo.findById(clipId)).thenReturn(Optional.of(clip));
        when(transcriptRepo.findByMediaAndLangAndProvider(media, "en", "whisper")).thenReturn(Optional.empty());
        when(storageService.resolveRaw("media-key")).thenReturn(rawPath);
        RenderResult renderResult = new RenderResult("clip.mp4", 5_000L, "thumb.png", 900L);
        when(clipRenderEngine.render(eq(rawPath), eq(clip.getStartMs()), eq(clip.getEndMs()), any(RenderOptions.class)))
                .thenReturn(renderResult);

        workerService.handleClip(job);

        ArgumentCaptor<RenderOptions> optionsCaptor = ArgumentCaptor.forClass(RenderOptions.class);
        verify(clipRenderEngine).render(eq(rawPath), eq(clip.getStartMs()), eq(clip.getEndMs()), optionsCaptor.capture());
        RenderOptions options = optionsCaptor.getValue();
        assertEquals(clip.getMeta(), options.meta());
        assertNull(options.subtitles());

        verify(subtitleService, never()).buildSubtitles(any(), anyLong(), anyLong());

        ArgumentCaptor<Asset> assetCaptor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepo, times(2)).save(assetCaptor.capture());
        List<Asset> assets = assetCaptor.getAllValues();
        Asset mp4Asset = assets.get(0);
        assertEquals(AssetKind.CLIP_MP4, mp4Asset.getKind());
        assertEquals(5_000L, mp4Asset.getSizeBytes());
        Asset thumbAsset = assets.get(1);
        assertEquals(AssetKind.THUMBNAIL, thumbAsset.getKind());
        assertEquals(900L, thumbAsset.getSizeBytes());

        assertNull(clip.getCaptionSrtKey());
        assertNull(clip.getCaptionVttKey());
        assertEquals(ClipStatus.READY, clip.getStatus());
        verify(clipRepo).save(clip);
        verify(jobService).markDone(isNull(), Map.of("mp4Key", "clip.mp4"));
    }

    @Test
    void handleClipWithMissingArtifactsShouldOnlyPersistMp4() throws Exception {
        UUID clipId = UUID.randomUUID();
        Account owner = new Account("acc-3", "Owner");
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setOwner(owner);
        media.setObjectKey("media-key");

        Clip clip = new Clip(media, 3_000, 8_000);
        clip.setStatus(ClipStatus.RENDERING);
        clip.setMeta(Map.of("theme", "minimal"));

        Transcript transcript = new Transcript(media, "en", "whisper");

        Job job = new Job(JobType.CLIP);
        job.setPayload(new HashMap<>(Map.of("clipId", clipId.toString())));

        Path rawPath = Path.of("/tmp/media-key.mp4");
        when(clipRepo.findById(clipId)).thenReturn(Optional.of(clip));
        when(transcriptRepo.findByMediaAndLangAndProvider(media, "en", "whisper")).thenReturn(Optional.of(transcript));
        SubtitleFiles subtitleFiles = new SubtitleFiles(null, 0L, null, 0L);
        when(subtitleService.buildSubtitles(transcript, clip.getStartMs(), clip.getEndMs())).thenReturn(subtitleFiles);
        when(storageService.resolveRaw("media-key")).thenReturn(rawPath);
        RenderResult renderResult = new RenderResult("clip.mp4", 4_200L, null, 0L);
        when(clipRenderEngine.render(eq(rawPath), eq(clip.getStartMs()), eq(clip.getEndMs()), any(RenderOptions.class)))
                .thenReturn(renderResult);

        workerService.handleClip(job);

        ArgumentCaptor<RenderOptions> optionsCaptor = ArgumentCaptor.forClass(RenderOptions.class);
        verify(clipRenderEngine).render(eq(rawPath), eq(clip.getStartMs()), eq(clip.getEndMs()), optionsCaptor.capture());
        RenderOptions options = optionsCaptor.getValue();
        assertEquals(clip.getMeta(), options.meta());
        assertSame(subtitleFiles, options.subtitles());

        ArgumentCaptor<Asset> assetCaptor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepo, times(1)).save(assetCaptor.capture());
        Asset mp4Asset = assetCaptor.getValue();
        assertEquals(AssetKind.CLIP_MP4, mp4Asset.getKind());
        assertEquals("clip.mp4", mp4Asset.getObjectKey());
        assertEquals(4_200L, mp4Asset.getSizeBytes());

        assertNull(clip.getCaptionSrtKey());
        assertNull(clip.getCaptionVttKey());
        assertEquals(ClipStatus.READY, clip.getStatus());
        verify(clipRepo).save(clip);
        verify(jobService).markDone(isNull(), Map.of("mp4Key", "clip.mp4"));
    }
}
