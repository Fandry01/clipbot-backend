package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.orchestrate.OneClickJob;
import com.example.clipbot_backend.dto.orchestrate.OneClickRequest;
import com.example.clipbot_backend.dto.orchestrate.OneClickResponse;
import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.OneClickOrchestration;
import com.example.clipbot_backend.model.Project;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.OneClickOrchestrationRepository;
import com.example.clipbot_backend.service.metadata.MetadataResult;
import com.example.clipbot_backend.service.metadata.MetadataService;
import com.example.clipbot_backend.util.MediaPlatform;
import com.example.clipbot_backend.util.OrchestrationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OneClickOrchestratorTest {

    @Mock
    private MetadataService metadataService;
    @Mock
    private ProjectService projectService;
    @Mock
    private MediaService mediaService;
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private DetectionService detectionService;
    @Mock
    private RecommendationService recommendationService;
    @Mock
    private OneClickOrchestrationRepository orchestrationRepository;

    private ObjectMapper objectMapper;

    private OneClickOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        orchestrator = new OneClickOrchestrator(
                metadataService,
                projectService,
                mediaService,
                mediaRepository,
                detectionService,
                recommendationService,
                orchestrationRepository,
                objectMapper
        );
        when(orchestrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void orchestrateCreatesProjectFromUrlAndEnqueuesFlow() {
        UUID mediaId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Account owner = new Account();
        owner.setId(UUID.randomUUID());
        owner.setExternalSubject("demo-user");
        Project project = new Project(owner, "My import", null, "https://normalized");
        project.setId(projectId);

        OneClickRequest request = new OneClickRequest(
                owner.getExternalSubject(),
                "https://youtube.com/watch?v=abc",
                null,
                "My import",
                new OneClickRequest.Options("auto", "fasterwhisper", 0.3, 6, true),
                "idem-1"
        );

        MetadataResult metadata = new MetadataResult(MediaPlatform.YOUTUBE, "https://normalized", "Meta title", "auth", 120L, "thumb");
        when(metadataService.resolve(anyString())).thenReturn(metadata);
        when(orchestrationRepository.findByOwnerExternalSubjectAndIdempotencyKey(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(projectService.findByNormalizedUrl(anyString(), anyString())).thenReturn(Optional.empty());
        when(projectService.createProjectBySubject(anyString(), anyString(), any(), any())).thenReturn(project);
        when(mediaService.createMediaFromUrl(any(), any(), any(), anyString(), any(), any())).thenReturn(mediaId);
        when(detectionService.enqueueDetect(any(), anyString(), anyString(), any())).thenReturn(jobId);
        when(recommendationService.computeRecommendations(any(), anyInt(), any(), any())).thenReturn(new com.example.clipbot_backend.dto.RecommendationResult(mediaId, 2, java.util.List.of()));

        OneClickResponse response = orchestrator.orchestrate(request);

        assertThat(response.getProjectId()).isEqualTo(projectId);
        assertThat(response.getMediaId()).isEqualTo(mediaId);
        assertThat(response.isCreatedProject()).isTrue();
        assertThat(response.getDetectJob()).isEqualTo(new OneClickJob(jobId, "ENQUEUED"));
        assertThat(response.getRecommendations().computed()).isEqualTo(2);
        assertThat(response.getThumbnailSource()).isEqualTo("YOUTUBE");

        verify(projectService).patch(projectId, org.mockito.ArgumentMatchers.any());
    }

    @Test
    void orchestrateUsesExistingMediaId() {
        UUID mediaId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Account owner = new Account();
        owner.setId(ownerId);
        owner.setExternalSubject("demo-user");
        Project project = new Project(owner, "New project", null, null);
        project.setId(projectId);

        com.example.clipbot_backend.model.Media media = new com.example.clipbot_backend.model.Media();
        media.setId(mediaId);
        media.setOwner(owner);

        OneClickRequest request = new OneClickRequest(
                owner.getExternalSubject(),
                null,
                mediaId,
                null,
                new OneClickRequest.Options(null, null, null, null, null),
                "idem-2"
        );

        when(projectService.findByNormalizedUrl(anyString(), any())).thenReturn(Optional.empty());
        when(orchestrationRepository.findByOwnerExternalSubjectAndIdempotencyKey(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(projectService.createProjectBySubject(anyString(), anyString(), any(), any())).thenReturn(project);
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        when(detectionService.enqueueDetect(any(), anyString(), anyString(), any())).thenReturn(jobId);
        when(recommendationService.computeRecommendations(any(), anyInt(), any(), any())).thenReturn(new com.example.clipbot_backend.dto.RecommendationResult(mediaId, 1, java.util.List.of()));

        OneClickResponse response = orchestrator.orchestrate(request);

        assertThat(response.getMediaId()).isEqualTo(mediaId);
        assertThat(response.isCreatedProject()).isTrue();
        verify(mediaService, never()).createMediaFromUrl(any(), any(), any(), anyString(), any(), any());
    }

    @Test
    void orchestrateReplaysSuccessfulIdempotentCall() {
        UUID mediaId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        OneClickResponse stored = OneClickResponse.builder()
                .projectId(projectId)
                .mediaId(mediaId)
                .createdProject(true)
                .detectJob(new OneClickJob(UUID.randomUUID(), "ENQUEUED"))
                .recommendations(new com.example.clipbot_backend.dto.orchestrate.OneClickRecommendation(6, 6))
                .renderJobs(java.util.List.of())
                .thumbnailSource("NONE")
                .build();
        OneClickOrchestration entity = new OneClickOrchestration("demo", "idem-3");
        entity.setStatus(OrchestrationStatus.SUCCEEDED);
        try {
            entity.setResponsePayload(objectMapper.writeValueAsString(stored));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        OneClickRequest request = new OneClickRequest("demo", "https://example.com", null, null, null, "idem-3");

        when(orchestrationRepository.findByOwnerExternalSubjectAndIdempotencyKey("demo", "idem-3"))
                .thenReturn(Optional.of(entity));

        OneClickResponse response = orchestrator.orchestrate(request);

        assertThat(response.getProjectId()).isEqualTo(projectId);
        verify(detectionService, never()).enqueueDetect(any(), anyString(), anyString(), any());
    }

    @Test
    void orchestrateValidatesExclusiveInputs() {
        OneClickRequest request = new OneClickRequest("demo", "https://example.com", UUID.randomUUID(), null, null, "idem-4");

        assertThatThrownBy(() -> orchestrator.orchestrate(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("URL_XOR_MEDIA_ID_REQUIRED")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
