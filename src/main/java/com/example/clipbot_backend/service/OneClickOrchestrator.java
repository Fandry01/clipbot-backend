package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.ProjectPatch;
import com.example.clipbot_backend.dto.media.CreateFromUrlResponse;
import com.example.clipbot_backend.dto.orchestrate.OneClickJob;
import com.example.clipbot_backend.dto.orchestrate.OneClickRecommendation;
import com.example.clipbot_backend.dto.orchestrate.OneClickRequest;
import com.example.clipbot_backend.dto.orchestrate.OneClickResponse;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.OneClickOrchestration;
import com.example.clipbot_backend.model.Project;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.OneClickOrchestrationRepository;
import com.example.clipbot_backend.repository.SegmentRepository;
import com.example.clipbot_backend.repository.TranscriptRepository;
import com.example.clipbot_backend.service.metadata.MetadataResult;
import com.example.clipbot_backend.service.metadata.MetadataService;
import com.example.clipbot_backend.util.MediaPlatform;
import com.example.clipbot_backend.util.OrchestrationStatus;
import com.example.clipbot_backend.util.ThumbnailSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

/**
 * Service that orchestrates the complete ingest + detect + recommend flow in a single call.
 */
@Service
public class OneClickOrchestrator {
    private static final Logger LOGGER = LoggerFactory.getLogger(OneClickOrchestrator.class);
    private static final int DEFAULT_TOP_N = 6;

    private final MetadataService metadataService;
    private final ProjectService projectService;
    private final MediaService mediaService;
    private final MediaRepository mediaRepository;
    private final DetectionService detectionService;
    private final RecommendationService recommendationService;
    private final SegmentRepository segmentRepository;
    private final TranscriptRepository transcriptRepository;
    private final OneClickOrchestrationRepository orchestrationRepository;
    private final ObjectMapper objectMapper;

    public OneClickOrchestrator(MetadataService metadataService,
                                ProjectService projectService,
                                MediaService mediaService,
                                MediaRepository mediaRepository,
                                DetectionService detectionService,
                                RecommendationService recommendationService,
                                SegmentRepository segmentRepository,
                                TranscriptRepository transcriptRepository,
                                OneClickOrchestrationRepository orchestrationRepository,
                                ObjectMapper objectMapper) {
        this.metadataService = metadataService;
        this.projectService = projectService;
        this.mediaService = mediaService;
        this.mediaRepository = mediaRepository;
        this.detectionService = detectionService;
        this.recommendationService = recommendationService;
        this.segmentRepository = segmentRepository;
        this.transcriptRepository = transcriptRepository;
        this.orchestrationRepository = orchestrationRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Runs the orchestration flow atomically with idempotency protection.
     *
     * @param request validated request payload.
     * @return orchestration response with identifiers and job details.
     */
    @Transactional
    public OneClickResponse orchestrate(OneClickRequest request) {
        validateRequest(request);
        LOGGER.info("OneClickOrchestrator START owner={} idempotencyKey={}", request.ownerExternalSubject(), request.idempotencyKey());

        OneClickOrchestration orchestration = orchestrationRepository
                .findByOwnerExternalSubjectAndIdempotencyKey(request.ownerExternalSubject(), request.idempotencyKey())
                .map(existing -> handleExistingOrchestration(existing, request))
                .orElseGet(() -> createOrchestrationRecord(request));

        if (orchestration.getStatus() == OrchestrationStatus.SUCCEEDED && orchestration.getResponsePayload() != null) {
            return deserialize(orchestration.getResponsePayload());
        }

        try {
            MetadataResult metadata = resolveMetadata(request);
            String resolvedTitle = resolveTitle(request, metadata);
            ProjectResolution projectResolution = resolveProject(request.ownerExternalSubject(), metadata, resolvedTitle);

            UUID mediaId = resolveMedia(request, metadata, projectResolution.project());
            linkMedia(projectResolution.project().getId(), mediaId, request.ownerExternalSubject());

            OneClickRequest.Options options = request.opts() == null ? new OneClickRequest.Options(null, null, null, null, null) : request.opts();
            OneClickJob detectJob = enqueueDetect(mediaId, options);
            OneClickRecommendation recs = enqueueRecommendationsIfReady(mediaId, request.ownerExternalSubject(), options);

            ThumbnailSource thumbnailSource = applyThumbnailIfPresent(projectResolution.project().getId(), metadata);

            OneClickResponse response = OneClickResponse.builder()
                    .projectId(projectResolution.project().getId())
                    .mediaId(mediaId)
                    .createdProject(projectResolution.created())
                    .detectJob(detectJob)
                    .recommendations(recs)
                    .renderJobs(Collections.emptyList())
                    .thumbnailSource(thumbnailSource.name())
                    .build();

            orchestration.setStatus(OrchestrationStatus.SUCCEEDED);
            orchestration.setResponsePayload(serialize(response));
            orchestrationRepository.save(orchestration);
            LOGGER.info("OneClickOrchestrator SCHEDULED owner={} projectId={} mediaId={} idempotencyKey={}",
                    request.ownerExternalSubject(), projectResolution.project().getId(), mediaId, request.idempotencyKey());
            return response;
        } catch (ResponseStatusException ex) {
            orchestration.setStatus(OrchestrationStatus.FAILED);
            orchestrationRepository.save(orchestration);
            throw ex;
        } catch (RuntimeException ex) {
            orchestration.setStatus(OrchestrationStatus.FAILED);
            orchestrationRepository.save(orchestration);
            throw ex;
        }
    }

    private MetadataResult resolveMetadata(OneClickRequest request) {
        if (request.url() == null || request.url().isBlank()) {
            return null;
        }
        try {
            return metadataService.resolve(request.url());
        } catch (ResponseStatusException ex) {
            LOGGER.warn("Metadata resolve failed url={} reason={} status={}", request.url(), ex.getReason(), ex.getStatusCode());
            throw ex;
        }
    }

    private String resolveTitle(OneClickRequest request, MetadataResult metadata) {
        if (request.title() != null && !request.title().isBlank()) {
            return request.title().trim();
        }
        if (metadata != null && metadata.title() != null && !metadata.title().isBlank()) {
            return metadata.title();
        }
        return "New project";
    }

    private ProjectResolution resolveProject(String ownerExternalSubject, MetadataResult metadata, String title) {
        String normalizedUrl = metadata != null ? metadata.url() : null;
        Optional<Project> existing = projectService.findByNormalizedUrl(ownerExternalSubject, normalizedUrl);
        if (existing.isPresent()) {
            Project project = existing.get();
            LOGGER.info("OneClickOrchestrator reuseProject owner={} projectId={} normalizedUrl={}", ownerExternalSubject, project.getId(), normalizedUrl);
            return new ProjectResolution(project, false);
        }
        Project created = projectService.createProjectBySubject(ownerExternalSubject, title, null, normalizedUrl);
        return new ProjectResolution(created, true);
    }

    private UUID resolveMedia(OneClickRequest request, MetadataResult metadata, Project project) {
        if (request.mediaId() != null) {
            Media media = mediaRepository.findById(request.mediaId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));
            String ownerSubject = media.getOwner() != null ? media.getOwner().getExternalSubject() : null;
            if (!request.ownerExternalSubject().equals(ownerSubject)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "MEDIA_NOT_OWNED");
            }
            return media.getId();
        }
        if (request.url() == null || request.url().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL_REQUIRED");
        }

        MediaPlatform platform = metadata != null ? metadata.platform() : MediaPlatform.OTHER;
        Long durationMs = metadata != null && metadata.durationSec() != null ? metadata.durationSec() * 1000 : null;
        CreateFromUrlResponse created = new CreateFromUrlResponse(
                mediaService.createMediaFromUrl(project.getOwner().getId(),
                        metadata != null ? metadata.url() : request.url(),
                        platform,
                        "ingest",
                        durationMs,
                        null)
        );
        return created.mediaId();
    }

    private void linkMedia(UUID projectId, UUID mediaId, String ownerExternalSubject) {
        projectService.linkMediaStrict(projectId, mediaId);
        LOGGER.info("OneClickOrchestrator linked media owner={} projectId={} mediaId={}", ownerExternalSubject, projectId, mediaId);
    }

    private OneClickJob enqueueDetect(UUID mediaId, OneClickRequest.Options options) {
        String lang = options.normalizedLang();
        String provider = options.normalizedProvider();
        Double sceneThreshold = options.normalizedSceneThreshold();
        Integer requested = options.resolvedTopN(DEFAULT_TOP_N);
        Boolean enqueueRender = options.shouldEnqueueRender(true);
        UUID jobId = detectionService.enqueueDetect(
                mediaId,
                lang == null ? "auto" : lang,
                provider == null ? "fasterwhisper" : provider,
                sceneThreshold,
                requested,
                enqueueRender
        );
        return new OneClickJob(jobId, "ENQUEUED");
    }

    private OneClickRecommendation enqueueRecommendationsIfReady(UUID mediaId, String ownerExternalSubject, OneClickRequest.Options options) {
        int requested = options.resolvedTopN(DEFAULT_TOP_N);
        boolean enqueueRender = options.shouldEnqueueRender(true);
        boolean detectReady = transcriptRepository.existsByMediaId(mediaId)
                && segmentRepository.countByMediaId(mediaId) > 0;

        if (!detectReady) {
            LOGGER.info("OneClickOrchestrator recommendations deferred owner={} mediaId={} requested={} reason=detect_pending", ownerExternalSubject, mediaId, requested);
            return new OneClickRecommendation(requested, 0);
        }

        int computed = recommendationService.computeRecommendations(mediaId, requested, null, enqueueRender).count();
        LOGGER.info("OneClickOrchestrator recommendations owner={} mediaId={} requested={} computed={} enqueueRender={}", ownerExternalSubject, mediaId, requested, computed, enqueueRender);
        return new OneClickRecommendation(requested, computed);
    }

    private ThumbnailSource applyThumbnailIfPresent(UUID projectId, MetadataResult metadata) {
        if (metadata != null && metadata.platform() == MediaPlatform.YOUTUBE && metadata.thumbnail() != null) {
            projectService.patch(projectId, ProjectPatch.builder().thumbnailUrl(metadata.thumbnail()).build());
            return ThumbnailSource.YOUTUBE;
        }
        return ThumbnailSource.NONE;
    }

    private OneClickOrchestration createOrchestrationRecord(OneClickRequest request) {
        OneClickOrchestration orchestration = new OneClickOrchestration(request.ownerExternalSubject(), request.idempotencyKey());
        orchestrationRepository.save(orchestration);
        return orchestration;
    }

    private OneClickOrchestration handleExistingOrchestration(OneClickOrchestration orchestration, OneClickRequest request) {
        if (!request.ownerExternalSubject().equals(orchestration.getOwnerExternalSubject())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_OWNER_MISMATCH");
        }
        if (orchestration.getStatus() == OrchestrationStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ORCHESTRATION_IN_PROGRESS");
        }
        return orchestration;
    }

    private void validateRequest(OneClickRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "REQUEST_REQUIRED");
        }
        if (request.ownerExternalSubject() == null || request.ownerExternalSubject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OWNER_REQUIRED");
        }
        boolean hasUrl = request.url() != null && !request.url().isBlank();
        boolean hasMedia = request.mediaId() != null;
        if (hasUrl == hasMedia) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL_XOR_MEDIA_ID_REQUIRED");
        }
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
        }
    }

    private String serialize(OneClickResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize response", ex);
        }
    }

    private OneClickResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, OneClickResponse.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize stored response", ex);
        }
    }

    private record ProjectResolution(Project project, boolean created) {
    }
}
