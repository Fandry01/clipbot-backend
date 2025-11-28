package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.render.SubtitleStyle;
import com.example.clipbot_backend.engine.Interfaces.ClipRenderEngine;
import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Asset;
import com.example.clipbot_backend.model.Clip;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.repository.AssetRepository;
import com.example.clipbot_backend.repository.ClipRepository;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.example.clipbot_backend.util.AssetKind;
import com.example.clipbot_backend.util.JobType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenderServiceTest {

    @Mock
    private ClipRepository clipRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private JobService jobService;
    @Mock
    private StorageService storageService;
    @Mock
    private ClipRenderEngine renderEngine;

    private RenderService renderService;

    @BeforeEach
    void setUp() {
        renderService = new RenderService(clipRepository, assetRepository, jobService, storageService, renderEngine, new ObjectMapper());
    }

    @Test
    void enqueueExportThrowsWhenSubtitlesMissing() {
        Clip clip = sampleClip();
        when(clipRepository.findById(clip.getId())).thenReturn(Optional.of(clip));
        when(assetRepository.findTopByRelatedClipAndKindOrderByCreatedAtDesc(clip, AssetKind.SUB_VTT)).thenReturn(Optional.empty());
        when(assetRepository.findTopByRelatedClipAndKindOrderByCreatedAtDesc(clip, AssetKind.SUB_SRT)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> renderService.enqueueExportWithStyle(clip.getId(), SubtitleStyle.defaults(), null));
    }

    @Test
    void enqueueExportEnqueuesJobWithDefaults() {
        Clip clip = sampleClip();
        when(clipRepository.findById(clip.getId())).thenReturn(Optional.of(clip));
        Asset vtt = new Asset(clip.getMedia().getOwner(), AssetKind.SUB_VTT, "subs/test.vtt", 10);
        when(assetRepository.findTopByRelatedClipAndKindOrderByCreatedAtDesc(clip, AssetKind.SUB_VTT)).thenReturn(Optional.of(vtt));
        when(assetRepository.findTopByRelatedClipAndKindOrderByCreatedAtDesc(clip, AssetKind.SUB_SRT)).thenReturn(Optional.empty());
        UUID jobId = UUID.randomUUID();
        when(jobService.enqueue(any(), any(JobType.class), any(Map.class))).thenReturn(jobId);

        UUID result = renderService.enqueueExportWithStyle(clip.getId(), null, "profile");

        assertThat(result).isEqualTo(jobId);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jobService).enqueue(any(), any(JobType.class), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).containsKeys("clipId", "subtitleStyle");
    }

    private Clip sampleClip() {
        Account owner = new Account();
        owner.setExternalSubject("user-1");
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setOwner(owner);
        media.setObjectKey("clips/source.mp4");

        Clip clip = new Clip(media, 0, 10_000);
        clip.setStatus(com.example.clipbot_backend.util.ClipStatus.READY);
        clip.setTitle("clip");
        clip.setMeta(Map.of());
        clip.setProfileHash("");
        ReflectionTestUtils.setField(clip, "id", UUID.randomUUID());
        return clip;
    }
}
