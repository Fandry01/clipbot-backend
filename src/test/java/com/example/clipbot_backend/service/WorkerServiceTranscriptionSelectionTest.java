package com.example.clipbot_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.clipbot_backend.engine.Interfaces.ClipRenderEngine;
import com.example.clipbot_backend.engine.Interfaces.DetectionEngine;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Job;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.repository.AssetRepository;
import com.example.clipbot_backend.repository.ClipRepository;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.SegmentRepository;
import com.example.clipbot_backend.repository.TranscriptRepository;
import com.example.clipbot_backend.repository.ProjectMediaRepository;
import com.example.clipbot_backend.service.AudioWindowService;
import com.example.clipbot_backend.service.ClipService;
import com.example.clipbot_backend.service.ClipWorkFlow;
import com.example.clipbot_backend.service.DetectWorkflow;
import com.example.clipbot_backend.service.FasterWhisperClient;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.example.clipbot_backend.service.Interfaces.SubtitleService;
import com.example.clipbot_backend.service.IngestCleanupService;
import com.example.clipbot_backend.service.RenderService;
import com.example.clipbot_backend.service.UrlDownloader;
import com.example.clipbot_backend.service.thumbnail.ThumbnailService;
import com.example.clipbot_backend.util.JobType;
import com.example.clipbot_backend.util.SpeakerMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkerServiceTranscriptionSelectionTest {

    @Mock
    private JobService jobService;
    @Mock
    private TranscriptService transcriptService;
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private TranscriptRepository transcriptRepository;
    @Mock
    private SegmentRepository segmentRepository;
    @Mock
    private ClipRepository clipRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private ProjectMediaRepository projectMediaRepository;
    @Mock
    private UrlDownloader urlDownloader;
    @Mock
    private FasterWhisperClient fastWhisperClient;
    @Mock
    private AudioWindowService audioWindowService;
    @Mock
    private DetectWorkflow detectWorkflow;
    @Mock
    private ClipWorkFlow clipWorkFlow;
    @Mock
    private ClipService clipService;
    @Mock
    private DetectionEngine detectionEngine;
    @Mock
    private ClipRenderEngine clipRenderEngine;
    @Mock
    private StorageService storageService;
    @Mock
    private SubtitleService subtitleService;
    @Mock
    private RenderService renderService;
    @Mock
    private TranscriptionEngine gptEngine;
    @Mock
    private TranscriptionEngine fasterEngine;
    @Mock
    private ThumbnailService thumbnailService;
    @Mock
    private IngestCleanupService ingestCleanupService;

    private WorkerService workerService;
    private Path tempMedia;

    @BeforeEach
    void setup() throws Exception {
        var workerProps = new com.example.clipbot_backend.config.WorkerExecutorProperties();
        workerService = new WorkerService(jobService, transcriptService, mediaRepository, transcriptRepository, segmentRepository,
                clipRepository, assetRepository, projectMediaRepository, urlDownloader, fastWhisperClient, audioWindowService, detectWorkflow,
                clipWorkFlow, clipService, thumbnailService, ingestCleanupService, detectionEngine, clipRenderEngine, storageService, subtitleService, renderService,
                gptEngine, fasterEngine, Runnable::run, workerProps);
        tempMedia = Files.createTempFile("media", ".mp4");
        Files.write(tempMedia, new byte[]{1, 2, 3});
    }

    @Test
    void multiSpeakerUsesGptEngine() throws Exception {
        Media media = buildMedia(SpeakerMode.MULTI);
        Job job = buildJob(media);
        when(transcriptService.existsAnyFor(media.getId())).thenReturn(false);
        when(mediaRepository.findById(media.getId())).thenReturn(Optional.of(media));
        when(mediaRepository.findByIdWithOwner(media.getId())).thenReturn(Optional.of(media));
        when(storageService.resolveRaw(media.getObjectKey())).thenReturn(tempMedia);
        when(gptEngine.transcribe(any())).thenReturn(new TranscriptionEngine.Result("text", java.util.List.of(), "en", "GPT_DIARIZE", Map.of()));
        when(transcriptService.upsert(any(), any())).thenReturn(UUID.randomUUID());

        workerService.handleTranscribe(job);

        verify(gptEngine, times(1)).transcribe(any());
        verify(fasterEngine, never()).transcribe(any());
    }

    @Test
    void gptFailureFallsBackToFasterWhisper() throws Exception {
        Media media = buildMedia(SpeakerMode.MULTI);
        Job job = buildJob(media);
        when(transcriptService.existsAnyFor(media.getId())).thenReturn(false);
        when(mediaRepository.findById(media.getId())).thenReturn(Optional.of(media));
        when(mediaRepository.findByIdWithOwner(media.getId())).thenReturn(Optional.of(media));
        when(storageService.resolveRaw(media.getObjectKey())).thenReturn(tempMedia);
        when(gptEngine.transcribe(any())).thenThrow(new RuntimeException("gpt failed"));
        when(fasterEngine.transcribe(any())).thenReturn(new TranscriptionEngine.Result("text", java.util.List.of(), "en", "FW", Map.of()));
        when(transcriptService.upsert(any(), any())).thenReturn(UUID.randomUUID());

        workerService.handleTranscribe(job);

        verify(gptEngine, times(1)).transcribe(any());
        verify(fasterEngine, times(1)).transcribe(any());
    }

    @Test
    void singleSpeakerUsesFasterWhisper() throws Exception {
        Media media = buildMedia(SpeakerMode.SINGLE);
        Job job = buildJob(media);
        when(transcriptService.existsAnyFor(media.getId())).thenReturn(false);
        when(mediaRepository.findById(media.getId())).thenReturn(Optional.of(media));
        when(mediaRepository.findByIdWithOwner(media.getId())).thenReturn(Optional.of(media));
        when(storageService.resolveRaw(media.getObjectKey())).thenReturn(tempMedia);
        when(fasterEngine.transcribe(any())).thenReturn(new TranscriptionEngine.Result("text", java.util.List.of(), "en", "FW", Map.of()));
        when(transcriptService.upsert(any(), any())).thenReturn(UUID.randomUUID());

        workerService.handleTranscribe(job);

        verify(fasterEngine, times(1)).transcribe(any());
        verify(gptEngine, never()).transcribe(any());
    }

    @Test
    void downloadFailureTriggersCleanup() {
        Media media = buildMedia(SpeakerMode.SINGLE);
        media.setSource("url");
        media.setExternalUrl("https://www.youtube.com/watch?v=video");
        Job job = buildJob(media);

        when(transcriptService.existsAnyFor(media.getId())).thenReturn(false);
        when(mediaRepository.findById(media.getId())).thenReturn(Optional.of(media));
        when(mediaRepository.findByIdWithOwner(media.getId())).thenReturn(Optional.of(media));
        when(urlDownloader.ensureRawObject(anyString(), anyString())).thenThrow(new IllegalStateException("auth wall"));

        workerService.handleTranscribe(job);

        verify(ingestCleanupService).cleanupFailedIngest(eq(media.getId()), eq(job.getId()), eq(media.getObjectKey()), eq(media.getExternalUrl()), any(Throwable.class));
        verify(jobService).markError(eq(job.getId()), anyString(), anyMap());
        verify(jobService, never()).markDone(eq(job.getId()), any());
    }

    @Test
    void thumbnailUsesLocalVideoWhenAvailable() throws Exception {
        Media media = buildMedia(SpeakerMode.SINGLE);
        media.setObjectKey("ext/yt/video/source.m4a");
        Job job = buildJob(media);

        when(transcriptService.existsAnyFor(media.getId())).thenReturn(false);
        when(mediaRepository.findById(media.getId())).thenReturn(Optional.of(media));
        when(mediaRepository.findByIdWithOwner(media.getId())).thenReturn(Optional.of(media));

        Path dir = Files.createTempDirectory("thumb-src");
        Path m4a = dir.resolve("source.m4a");
        Path mp4 = dir.resolve("source.mp4");
        Files.writeString(m4a, "audio");
        Files.writeString(mp4, "video");
        when(storageService.resolveRaw(media.getObjectKey())).thenReturn(m4a);
        when(fasterEngine.transcribe(any())).thenReturn(new TranscriptionEngine.Result("text", java.util.List.of(), "en", "FW", Map.of()));
        when(transcriptService.upsert(any(), any())).thenReturn(UUID.randomUUID());

        workerService.handleTranscribe(job);

        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        ArgumentCaptor<ThumbnailService.ThumbnailRequest> reqCaptor = ArgumentCaptor.forClass(ThumbnailService.ThumbnailRequest.class);
        verify(thumbnailService).extractFromLocalMedia(reqCaptor.capture(), pathCaptor.capture());
        assertEquals(mp4, pathCaptor.getValue());
        assertEquals(media.getId(), reqCaptor.getValue().mediaId());
    }

    private Media buildMedia(SpeakerMode mode) {
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setSpeakerMode(mode);
        media.setObjectKey("object-key.mp4");
        media.setSource("upload");
        media.setOwner(new Account("ext", "owner"));
        return media;
    }

    private Job buildJob(Media media) {
        Job job = new Job(JobType.TRANSCRIBE);
        job.setId(UUID.randomUUID());
        job.setMedia(media);
        job.setPayload(Map.of());
        return job;
    }
}
