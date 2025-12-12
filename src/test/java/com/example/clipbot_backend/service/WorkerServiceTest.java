package com.example.clipbot_backend.service;

import com.example.clipbot_backend.engine.Interfaces.ClipRenderEngine;
import com.example.clipbot_backend.engine.Interfaces.DetectionEngine;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.model.Job;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.repository.*;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.example.clipbot_backend.service.Interfaces.SubtitleService;
import com.example.clipbot_backend.util.JobType;
import com.example.clipbot_backend.util.SpeakerMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerServiceTest {

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
    private UrlDownloader urlDownloader;
    @Mock
    private FasterWhisperClient fasterWhisperClient;
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
    private TranscriptionEngine gptDiarizeEngine;
    @Mock
    private TranscriptionEngine fasterWhisperEngine;

    @TempDir
    Path tempDir;

    @Test
    void handleTranscribeUsesGptForMultiSpeaker() throws Exception {
        WorkerService workerService = new WorkerService(
                jobService,
                transcriptService,
                mediaRepository,
                transcriptRepository,
                segmentRepository,
                clipRepository,
                assetRepository,
                urlDownloader,
                fasterWhisperClient,
                audioWindowService,
                detectWorkflow,
                clipWorkFlow,
                clipService,
                detectionEngine,
                clipRenderEngine,
                storageService,
                subtitleService,
                renderService,
                gptDiarizeEngine,
                fasterWhisperEngine
        );

        UUID mediaId = UUID.randomUUID();
        Media media = new Media();
        media.setId(mediaId);
        media.setObjectKey("sample.raw");
        media.setSource("upload");
        media.setSpeakerMode(SpeakerMode.MULTI);

        Job job = new Job(JobType.TRANSCRIBE);
        job.setId(UUID.randomUUID());
        job.setMedia(media);

        Path rawFile = Files.createTempFile(tempDir, "media", ".mp3");
        when(storageService.resolveRaw("sample.raw")).thenReturn(rawFile);
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        when(transcriptService.existsAnyFor(mediaId)).thenReturn(false);

        TranscriptionEngine.Result result = new TranscriptionEngine.Result(
                "hello",
                List.of(),
                "nl",
                "gpt_diarize",
                Map.of()
        );
        when(gptDiarizeEngine.transcribe(any())).thenReturn(result);

        workerService.handleTranscribe(job);

        verify(gptDiarizeEngine).transcribe(any(TranscriptionEngine.Request.class));
        verify(fasterWhisperEngine, never()).transcribe(any());
        verify(jobService).enqueue(eq(mediaId), eq(JobType.DETECT), satisfiesPayload(result));
    }

    private ArgumentMatcher<Map<String, Object>> satisfiesPayload(TranscriptionEngine.Result result) {
        return payload -> result.lang().equals(payload.get("lang")) && result.provider().equals(payload.get("provider"));
    }
}
