package com.example.clipbot_backend.dto.web;

import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for the one-click orchestration endpoint.
 *
 * <p>It supports either ingesting by URL or reusing an already uploaded media id.
 * Exactly one of {@code url} or {@code mediaId} must be provided. The optional
 * {@code projectId} can be used to link the request to an existing project.</p>
 */
public record OneClickRequest(
        String ownerExternalSubject,
        String url,
        UUID mediaId,
        String title,
        Options opts,
        String idempotencyKey,
        UUID projectId
) {
    /**
     * Optional options block for the one-click orchestration flow.
     */
    public record Options(
            String provider,
            String lang,
            Integer topN,
            Boolean enqueueRender,
            Map<String, Object> profile,
            Double sceneThreshold
    ) {
    }
}
