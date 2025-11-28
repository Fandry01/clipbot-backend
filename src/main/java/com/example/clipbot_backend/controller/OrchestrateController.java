package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.RecommendationResult;
import com.example.clipbot_backend.dto.web.OneClickRequest;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.service.AccountService;
import com.example.clipbot_backend.service.DetectionService;
import com.example.clipbot_backend.service.ProjectService;
import com.example.clipbot_backend.service.MediaService;
import com.example.clipbot_backend.service.RecommendationService;
import com.example.clipbot_backend.repository.MediaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/v1/orchestrate")
public class OrchestrateController {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrchestrateController.class);
    private static final long PODCAST_THRESHOLD_MS = 25 * 60 * 1000L;

    private final AccountService accountService;
    private final ProjectService projectService;
    private final MediaRepository mediaRepository;
    private final MediaService mediaService;
    private final DetectionService detectionService;
    private final RecommendationService recommendationService;

    public OrchestrateController(AccountService accountService,
                                 ProjectService projectService,
                                 MediaRepository mediaRepository,
                                 MediaService mediaService,
                                 DetectionService detectionService,
                                 RecommendationService recommendationService) {
        this.accountService = accountService;
        this.projectService = projectService;
        this.mediaRepository = mediaRepository;
        this.mediaService = mediaService;
        this.detectionService = detectionService;
        this.recommendationService = recommendationService;
    }

    @PostMapping("/one-click")
    public ResponseEntity<Map<String, Object>> orchestrate(@RequestBody OneClickRequest request) {
        validateRequest(request);
        var owner = accountService.getByExternalSubjectOrThrow(request.ownerExternalSubject());

        Media media = resolveMedia(request, owner.getId());
        if (!Objects.equals(media.getOwner().getId(), owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "MEDIA_NOT_OWNED");
        }

        var projectResolution = resolveProject(request, media);
        UUID projectId = projectResolution.projectId();
        linkMedia(projectId, media.getId(), request.ownerExternalSubject());

        String provider = pickProvider(media, request.opts());
        UUID detectJobId = detectionService.enqueueDetect(media.getId(),
                request.opts() != null ? request.opts().lang() : null,
                provider,
                request.opts() != null ? request.opts().sceneThreshold() : null);

        RecommendationResult recs = recommendationService.computeRecommendations(
                media.getId(),
                request.opts() != null && request.opts().topN() != null ? request.opts().topN() : 6,
                request.opts() != null ? request.opts().profile() : Map.of(),
                request.opts() != null && Boolean.TRUE.equals(request.opts().enqueueRender())
        );

        LOGGER.info("oneClick owner={} key={} projectId={} mediaId={} provider={} lang={} topN={} enqueueRender={} status=ACCEPTED",
                request.ownerExternalSubject(),
                request.idempotencyKey(),
                projectId,
                media.getId(),
                provider,
                request.opts() != null ? request.opts().lang() : null,
                request.opts() != null ? request.opts().topN() : null,
                request.opts() != null && Boolean.TRUE.equals(request.opts().enqueueRender()));

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "projectId", projectId,
                        "mediaId", media.getId(),
                        "detectJobId", detectJobId,
                        "recommendations", recs.clips(),
                        "renderJobs", List.of()
                ));
    }

    private Media resolveMedia(OneClickRequest request, UUID ownerId) {
        if (request.mediaId() != null) {
            return mediaRepository.findById(request.mediaId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));
        }
        UUID mediaId = mediaService.createMediaFromUrl(ownerId, request.url(), null, "url", null, null);
        return mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));
    }

    private ProjectResolution resolveProject(OneClickRequest request, Media media) {
        if (request.projectId() != null) {
            var project = projectService.get(request.projectId());
            if (!Objects.equals(project.getOwner().getId(), media.getOwner().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PROJECT_NOT_OWNED");
            }
            LOGGER.info("reuseProjectById owner={} projectId={}", request.ownerExternalSubject(), project.getId());
            return new ProjectResolution(project.getId(), false);
        }
        var project = projectService.createProjectBySubject(
                request.ownerExternalSubject(),
                request.title() != null && !request.title().isBlank() ? request.title() : "New upload",
                null
        );
        return new ProjectResolution(project.getId(), true);
    }

    private void linkMedia(UUID projectId, UUID mediaId, String owner) {
        try {
            projectService.linkMediaStrict(projectId, mediaId);
            LOGGER.info("linked media owner={} projectId={} mediaId={}", owner, projectId, mediaId);
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                LOGGER.info("media already linked owner={} projectId={} mediaId={}", owner, projectId, mediaId);
                return;
            }
            throw ex;
        }
    }

    private String pickProvider(Media media, OneClickRequest.Options options) {
        if (options != null && options.provider() != null && !options.provider().isBlank()) {
            return options.provider();
        }
        Long duration = media.getDurationMs();
        if (duration != null && duration > PODCAST_THRESHOLD_MS) {
            return "openai-diarize";
        }
        return "fasterwhisper";
    }

    private void validateRequest(OneClickRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "REQUEST_BODY_REQUIRED");
        }
        if (request.ownerExternalSubject() == null || request.ownerExternalSubject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OWNER_REQUIRED");
        }
        boolean hasUrl = request.url() != null && !request.url().isBlank();
        boolean hasMedia = request.mediaId() != null;
        if (hasUrl == hasMedia) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL_OR_MEDIA_ID_REQUIRED");
        }
    }

    private record ProjectResolution(UUID projectId, boolean created) {
    }
}
