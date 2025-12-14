package com.example.clipbot_backend.service;

import com.example.clipbot_backend.engine.Interfaces.DetectionEngine;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.SegmentRepository;
import com.example.clipbot_backend.repository.TranscriptRepository;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DetectionServiceTest {

    @Mock JobService jobService;
    @Mock MediaRepository mediaRepo;
    @Mock TranscriptRepository transcriptRepo;
    @Mock SegmentRepository segmentRepo;
    @Mock StorageService storageService;
    @Mock DetectionEngine detectionEngine;

    @InjectMocks DetectionService detectionService;

    @Test
    void enqueueDetectFallsBackToTranscribeWhenTranscriptMissing() {
        UUID mediaId = UUID.randomUUID();
        Media media = new Media();
        media.setId(mediaId);

        when(mediaRepo.findById(mediaId)).thenReturn(Optional.of(media));
        when(transcriptRepo.existsByMediaId(mediaId)).thenReturn(false);

        detectionService.enqueueDetect(mediaId, "en", "provider", null, null, null);

        verify(jobService).enqueue(eq(mediaId), eq(com.example.clipbot_backend.util.JobType.TRANSCRIBE), eq(Map.of()));
    }

    @Test
    void enqueueDetectUsesDetectWhenTranscriptExists() {
        UUID mediaId = UUID.randomUUID();
        Media media = new Media();
        media.setId(mediaId);

        when(mediaRepo.findById(mediaId)).thenReturn(Optional.of(media));
        when(transcriptRepo.existsByMediaId(mediaId)).thenReturn(true);

        detectionService.enqueueDetect(mediaId, "en", "provider", 0.5, 3, true);

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(jobService).enqueue(eq(mediaId), eq(com.example.clipbot_backend.util.JobType.DETECT), payload.capture());
        assertThat(payload.getValue()).containsEntry("lang", "en");
        assertThat(payload.getValue()).containsEntry("provider", "provider");
        assertThat(payload.getValue()).containsEntry("sceneThreshold", 0.5);
        assertThat(payload.getValue()).containsEntry("topN", 3);
        assertThat(payload.getValue()).containsEntry("enqueueRender", true);
    }
}
