package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.api.dto.PageResponse;
import com.example.clipbot_backend.dto.ClipSummary;
import com.example.clipbot_backend.dto.RecommendationResult;
import com.example.clipbot_backend.dto.web.ComputeRequest;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.service.RecommendationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * REST controller exposing recommendation endpoints.
 */
@RestController
@RequestMapping("/v1/media/{mediaId}/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final MediaRepository mediaRepository;

    public RecommendationController(RecommendationService recommendationService,
                                    MediaRepository mediaRepository) {
        this.recommendationService = recommendationService;
        this.mediaRepository = mediaRepository;
    }

    /**
     * Computes recommendations for the provided media and stores the resulting clips.
     *
     * @param mediaId media identifier.
     * @param body    request payload with optional overrides.
     * @param ownerExternalSubject ownership marker used for authorization.
     * @return recommendation result with the selected clips.
     */
    @PostMapping("/compute")
    public RecommendationResult compute(@PathVariable UUID mediaId,
                                        @RequestBody(required = false) ComputeRequest body,
                                        @RequestParam String ownerExternalSubject) {
        Media media = ensureOwned(mediaId, ownerExternalSubject);
        int reqTopN = body != null && body.topN() != null ? body.topN() : 6;
        int topN = Math.max(1, Math.min(12, reqTopN));
        Map<String, Object> profile = body != null && body.profile() != null ? body.profile() : Map.of();
        boolean enqueue = body == null || body.enqueueRender() == null || body.enqueueRender();
        return recommendationService.computeRecommendations(media.getId(), topN, profile, enqueue);
    }

    /**
     * Lists stored recommendations for the media sorted by score.
     *
     * @param mediaId media identifier.
     * @param page    zero-based page index.
     * @param size    page size.
     * @param sort    sort expression formatted as "field,direction".
     * @param ownerExternalSubject ownership marker used for authorization.
     * @return pageable list of clip summaries.
     */
    @GetMapping
    public PageResponse<ClipSummary> list(@PathVariable UUID mediaId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(defaultValue = "score,desc") String sort,
                                          @RequestParam String ownerExternalSubject) {
        ensureOwned(mediaId, ownerExternalSubject);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), parseSort(sort));
        Page<ClipSummary> result = recommendationService.listRecommendations(mediaId, pageable);
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    /**
     * Returns a human-readable explanation for the requested window.
     *
     * @param mediaId media identifier.
     * @param startMs window start.
     * @param endMs   window end.
     * @param ownerExternalSubject ownership marker used for authorization.
     * @return feature breakdown map.
     */
    @GetMapping("/explain")
    public Map<String, String> explain(@PathVariable UUID mediaId,
                                       @RequestParam long startMs,
                                       @RequestParam long endMs,
                                       @RequestParam String ownerExternalSubject) {
        ensureOwned(mediaId, ownerExternalSubject);
        if (startMs < 0 || endMs <= startMs) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_WINDOW");
        }
        return recommendationService.explain(mediaId, startMs, endMs);
    }

    private Media ensureOwned(UUID mediaId, String ownerExternalSubject) {
        return mediaRepository.findOwned(mediaId, ownerExternalSubject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "MEDIA_NOT_OWNED_OR_NOT_FOUND"));
    }

    private static final Set<String> ALLOWED_SORT = Set.of("score", "createdAt", "startMs", "endMs");

    private static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Order.desc("score"), Sort.Order.desc("createdAt"));
        }
        String[] parts = sort.split(",");
        String prop = parts[0].trim();
        if (!ALLOWED_SORT.contains(prop)) {
            return Sort.by(Sort.Order.desc("score"), Sort.Order.desc("createdAt"));
        }
        Sort.Direction dir = (parts.length > 1 && "asc".equalsIgnoreCase(parts[1]))
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(new Sort.Order(dir, prop), Sort.Order.desc("createdAt"));
    }
}
