package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.ClipSummary;
import com.example.clipbot_backend.dto.RecommendationResult;
import jakarta.annotation.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

/**
 * Service responsible for generating and listing recommended clips.
 */
public interface RecommendationService {

    /**
     * Computes the top {@code N} clips for the given media and optionally enqueues render jobs.
     *
     * @param mediaId       media identifier.
     * @param topN          number of clips to keep (defaults to six when {@code topN <= 0}).
     * @param profile       optional render/profile metadata.
     * @param enqueueRender whether render jobs need to be enqueued immediately.
     * @return structured recommendation result.
     */
    RecommendationResult computeRecommendations(UUID mediaId, int topN, @Nullable Map<String, Object> profile, boolean enqueueRender);

    /**
     * Lists stored recommendations for a media item.
     *
     * @param mediaId media identifier.
     * @param pageable pagination request with sorting instructions.
     * @return a page of clip summaries ordered according to {@code pageable}.
     */
    Page<ClipSummary> listRecommendations(UUID mediaId, Pageable pageable);

    /**
     * Provides a debug explanation for a specific window.
     *
     * @param mediaId media identifier.
     * @param startMs start offset of the window.
     * @param endMs   end offset of the window.
     * @return explanation map or empty map if unknown.
     */
    Map<String, String> explain(UUID mediaId, long startMs, long endMs);
}
