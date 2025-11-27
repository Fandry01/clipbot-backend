package com.example.clipbot_backend.dto.orchestrate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request payload for the one-click orchestration endpoint.
 */
public record OneClickRequest(
        @NotBlank String ownerExternalSubject,
        String url,
        UUID mediaId,
        String title,
        Options opts,
        @NotBlank String idempotencyKey
) {
    /**
     * User-supplied options with sensible defaults applied by the orchestrator.
     */
    public record Options(String lang, String provider, Double sceneThreshold, Integer topN, Boolean enqueueRender) {
        public String normalizedLang() {
            return lang != null && !lang.isBlank() ? lang.trim() : null;
        }

        public String normalizedProvider() {
            return provider != null && !provider.isBlank() ? provider.trim() : null;
        }

        public Double normalizedSceneThreshold() {
            return sceneThreshold;
        }

        public int resolvedTopN(int fallback) {
            return topN != null && topN > 0 ? topN : fallback;
        }

        public boolean shouldEnqueueRender(boolean fallback) {
            return enqueueRender == null ? fallback : enqueueRender;
        }
    }
}
