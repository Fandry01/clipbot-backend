package com.example.clipbot_backend.selector;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicGoodClipSelectorTest {

    private final HeuristicGoodClipSelector selector = new HeuristicGoodClipSelector();

    @Test
    void selectsTopWindowsUsingDeterministicRanking() {
        SelectorConfig base = SelectorConfig.defaults();
        SelectorConfig cfg = new SelectorConfig(base.targetDurationSec(), base.minSpeechDensity(), base.maxSilencePenalty(), Set.of("launch", "market"), base.weights());
        Window w1 = new Window(0, 20_000, 0.60, 0.80, 0.50, 0.20, Set.of("launch"));
        Window w2 = new Window(5_000, 25_000, 0.82, 0.85, 0.70, 0.10, Set.of("market", "launch"));
        Window w3 = new Window(10_000, 30_000, 0.20, 0.60, 0.40, 0.90, Set.of()); // should be filtered (silence)
        Window w4 = new Window(15_000, 35_000, 0.58, 0.78, 0.55, 0.25, Set.of("growth"));

        List<ScoredWindow> result = selector.selectTop(List.of(w1, w2, w3, w4), 3, cfg);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).window()).isEqualTo(w2);
        assertThat(result.get(1).window()).isEqualTo(w1);
        assertThat(result.get(2).window()).isEqualTo(w4);
        assertThat(result.stream().map(ScoredWindow::window)).doesNotContain(w3);

        Map<String, String> explain = selector.explainLast();
        assertThat(explain).containsKey("5000-25000");
        assertThat(explain.get("5000-25000")).contains("kw=2");
    }
}
