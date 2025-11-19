package com.example.clipbot_backend.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response payload for recommendation computations.
 *
 * @param mediaId media identifier.
 * @param count   number of clips included in the result.
 * @param clips   ordered clip summaries.
 */
public record RecommendationResult(UUID mediaId, int count, List<ClipSummary> clips) {
}
