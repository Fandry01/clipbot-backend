package com.example.clipbot_backend.selector;

import java.util.List;
import java.util.Map;

/**
 * Selects the best windows for a media item based on heuristic scoring.
 */
public interface GoodClipSelector {
    /**
     * Selects the top {@code N} windows sorted by score in descending order.
     *
     * @param windows windows for a single media item.
     * @param topN    maximum number of results to return.
     * @param cfg     selector configuration to apply.
     * @return scored windows ordered by score.
     */
    List<ScoredWindow> selectTop(List<Window> windows, int topN, SelectorConfig cfg);

    /**
     * Returns the feature explanation for the last invocation of {@link #selectTop}.
     *
     * @return map keyed by "start-end" strings with feature breakdowns.
     */
    Map<String, String> explainLast();
}
