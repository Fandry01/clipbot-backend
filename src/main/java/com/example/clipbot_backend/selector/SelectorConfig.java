package com.example.clipbot_backend.selector;

import java.util.Map;
import java.util.Set;

/**
 * Configuration for the {@link GoodClipSelector}, including weights and filters.
 *
 * @param targetDurationSec preferred window duration in seconds.
 * @param minSpeechDensity  minimum acceptable speech density.
 * @param maxSilencePenalty maximum acceptable silence penalty.
 * @param boostKeywords     keywords that should receive extra attention.
 * @param weights           weighting map used by the heuristic selector.
 */
public record SelectorConfig(int targetDurationSec,
                             double minSpeechDensity,
                             double maxSilencePenalty,
                             Set<String> boostKeywords,
                             Map<String, Double> weights) {

    private static final Map<String, Double> DEFAULT_WEIGHTS = Map.of(
            "speech", 0.35,
            "conf", 0.25,
            "energy", 0.25,
            "silence", -0.15,
            "keyword", 0.10
    );

    /**
     * Provides a sensible default configuration tuned for news/podcast content.
     *
     * @return selector configuration with balanced weights and thresholds.
     */
    public static SelectorConfig defaults() {
        return new SelectorConfig(25, 0.35, 0.60, Set.of(), DEFAULT_WEIGHTS);
    }
}
