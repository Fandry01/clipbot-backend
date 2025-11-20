package com.example.clipbot_backend.selector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Default heuristic implementation that applies simple weights to window features.
 */
@Component
public class HeuristicGoodClipSelector implements GoodClipSelector {
    private static final Logger LOGGER = LoggerFactory.getLogger(HeuristicGoodClipSelector.class);
    private volatile Map<String, String> lastExplanation = Map.of();

    @Override
    public List<ScoredWindow> selectTop(List<Window> windows, int topN, SelectorConfig cfg) {
        SelectorConfig effective = cfg == null ? SelectorConfig.defaults() : cfg;
        int limit = Math.max(1, topN);
        List<ScoredWindow> scored = new ArrayList<>();
        Map<String, String> explanations = new LinkedHashMap<>();

        for (Window window : windows) {
            if (window == null) {
                continue;
            }
            if (window.speechDensity() < effective.minSpeechDensity()) {
                LOGGER.trace("selector skip start={} end={} speechDensity={} below min", window.startMs(), window.endMs(), window.speechDensity());
                continue;
            }
            if (window.silencePenalty() > effective.maxSilencePenalty()) {
                LOGGER.trace("selector skip start={} end={} silencePenalty={} above max", window.startMs(), window.endMs(), window.silencePenalty());
                continue;
            }

            double normSpeech = clamp(window.speechDensity());
            double normConf = clamp(window.avgConfidence());
            double normEnergy = clamp(window.textEnergy());
            double normSilence = clamp(1.0 - window.silencePenalty());
            long keywordMatches = window.keywords() == null ? 0 : window.keywords().size();
            long userMatches = countUserMatches(window.keywords(), effective.boostKeywords());
            double keywordBoost = Math.min(0.15, 0.03 * keywordMatches + 0.01 * userMatches);

            double wSpeech = effective.weights().getOrDefault("speech", 0.35);
            double wConf = effective.weights().getOrDefault("conf", 0.25);
            double wEnergy = effective.weights().getOrDefault("energy", 0.25);
            double wSilence = effective.weights().getOrDefault("silence", -0.15);
            double wKeyword = effective.weights().getOrDefault("keyword", 0.10);

            double score = (wSpeech * normSpeech)
                    + (wConf * normConf)
                    + (wEnergy * normEnergy)
                    + (wSilence * normSilence)
                    + (wKeyword * keywordBoost);

            scored.add(new ScoredWindow(window, score));
            String key = keyFor(window);
            String message = String.format(Locale.ROOT,
                    "S=%.2f C=%.2f E=%.2f Sil=%.2f kw=%d user=%d -> %.3f",
                    normSpeech, normConf, normEnergy, window.silencePenalty(), keywordMatches, userMatches, score);
            explanations.put(key, message);
            LOGGER.trace("selector window {} score={} details={}", key, String.format(Locale.ROOT, "%.3f", score), message);
        }

        scored.sort(Comparator.comparingDouble(ScoredWindow::score).reversed());
        if (scored.size() > limit) {
            scored = new ArrayList<>(scored.subList(0, limit));
        }
        lastExplanation = explanations;
        LOGGER.debug("HeuristicGoodClipSelector windows={} selected={} topScore={}",
                windows.size(), scored.size(), scored.isEmpty() ? "-" : String.format(Locale.ROOT, "%.3f", scored.getFirst().score()));
        return scored;
    }

    @Override
    public Map<String, String> explainLast() {
        return lastExplanation;
    }

    private static long countUserMatches(Set<String> keywords, Set<String> boostKeywords) {
        if (keywords == null || boostKeywords == null || boostKeywords.isEmpty()) {
            return 0;
        }
        return keywords.stream().filter(boostKeywords::contains).count();
    }

    private static String keyFor(Window window) {
        return window.startMs() + "-" + window.endMs();
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }
}
