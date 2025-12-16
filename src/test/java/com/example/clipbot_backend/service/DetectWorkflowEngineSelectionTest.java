package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.DetectionParams;
import com.example.clipbot_backend.engine.Interfaces.DetectionEngine;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Transcript;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.SegmentRepository;
import com.example.clipbot_backend.repository.TranscriptRepository;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.example.clipbot_backend.service.Interfaces.TranscriptService;
import com.example.clipbot_backend.util.MediaStatus;
import com.example.clipbot_backend.util.SpeakerMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DetectWorkflowEngineSelectionTest {

    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private TranscriptRepository transcriptRepository;
    @Mock
    private SegmentRepository segmentRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private DetectionEngine detectionEngine;
    @Mock
    private TranscriptService transcriptService;
    @Mock
    private TranscriptionEngine gptEngine;
    @Mock
    private TranscriptionEngine fasterEngine;
    @Mock
    private AudioWindowService audioWindowService;
    @Mock
    private UrlDownloader urlDownloader;
    @Mock
    private RecommendationService recommendationService;
    @Mock
    private com.example.clipbot_backend.service.thumbnail.ThumbnailService thumbnailService;

    private DetectWorkflow detectWorkflow;
    private Path tempFile;

    @BeforeEach
    void setup() throws Exception {
        detectWorkflow = new DetectWorkflow(
                mediaRepository,
                transcriptRepository,
                segmentRepository,
                storageService,
                detectionEngine,
                transcriptService,
                gptEngine,
                fasterEngine,
                audioWindowService,
                urlDownloader,
                recommendationService,
                thumbnailService
        );
        tempFile = Files.createTempFile("dw", ".mp4");
        Files.write(tempFile, new byte[]{1, 2, 3});
    }

    @Test
    void multiSpeakerUsesGptDiarizeEvenWhenJobProviderIsFw() throws Exception {
        Media media = buildMedia(SpeakerMode.MULTI);
        Transcript transcript = new Transcript();
        transcript.setMedia(media);
        UUID transcriptId = UUID.randomUUID();

        when(mediaRepository.findById(media.getId())).thenReturn(Optional.of(media));
        when(transcriptRepository.findTopByMediaOrderByCreatedAtDesc(media)).thenReturn(Optional.empty());
        when(storageService.resolveRaw(media.getObjectKey())).thenReturn(tempFile);
        when(gptEngine.transcribe(any())).thenReturn(new TranscriptionEngine.Result("text", List.of(), "en", "gpt", Map.of()));
        when(transcriptService.upsert(media.getId(), any())).thenReturn(transcriptId);
        when(transcriptRepository.findById(transcriptId)).thenReturn(Optional.of(transcript));
        when(segmentRepository.deleteByMedia(media)).thenReturn(0);
        when(detectionEngine.detect(any(Path.class), any(Transcript.class), any(DetectionParams.class)))
                .thenReturn(List.of());

        int count = detectWorkflow.run(media.getId(), Map.of("provider", "fw"));

        assertThat(count).isZero();
        verify(gptEngine).transcribe(any());
        verify(fasterEngine, never()).transcribe(any());
    }

    @Test
    void singleSpeakerStaysOnFasterWhisper() throws Exception {
        Media media = buildMedia(SpeakerMode.SINGLE);
        Transcript transcript = new Transcript();
        transcript.setMedia(media);
        UUID transcriptId = UUID.randomUUID();

        when(mediaRepository.findById(media.getId())).thenReturn(Optional.of(media));
        when(transcriptRepository.findTopByMediaOrderByCreatedAtDesc(media)).thenReturn(Optional.empty());
        when(storageService.resolveRaw(media.getObjectKey())).thenReturn(tempFile);
        when(fasterEngine.transcribe(any())).thenReturn(new TranscriptionEngine.Result("text", List.of(), "en", "fw", Map.of()));
        when(transcriptService.upsert(media.getId(), any())).thenReturn(transcriptId);
        when(transcriptRepository.findById(transcriptId)).thenReturn(Optional.of(transcript));
        when(segmentRepository.deleteByMedia(media)).thenReturn(0);
        when(detectionEngine.detect(any(Path.class), any(Transcript.class), any(DetectionParams.class)))
                .thenReturn(List.of());

        detectWorkflow.run(media.getId(), Map.of());

        verify(fasterEngine).transcribe(any());
        verify(gptEngine, never()).transcribe(any());
    }

    private Media buildMedia(SpeakerMode mode) {
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setSpeakerMode(mode);
        media.setStatus(MediaStatus.PROCESSING);
        media.setObjectKey("object-key.mp4");
        media.setSource("upload");
        media.setOwner(new Account("ext", "owner"));
        return media;
    }
}
