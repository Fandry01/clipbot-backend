package com.example.clipbot_backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.clipbot_backend.dto.SentenceSpan;
import com.example.clipbot_backend.dto.SpeakerTurn;
import java.util.List;
import org.junit.jupiter.api.Test;

class HeuristicScorerSpeakerTest {

    private final HeuristicScorer scorer = new HeuristicScorer();

    @Test
    void speakerAwareScoringRewardsAlignedBoundaries() {
        List<SentenceSpan> sentences = List.of(
                new SentenceSpan(0, 2000, "Hello there."),
                new SentenceSpan(2000, 4000, "Second sentence."),
                new SentenceSpan(4000, 7000, "Third line with punctuation!")
        );
        List<SpeakerTurn> turns = List.of(
                new SpeakerTurn("A", 0, 4000),
                new SpeakerTurn("B", 4000, 8000)
        );
        HeuristicScorer.SpeakerContext context = new HeuristicScorer.SpeakerContext(turns, true);

        var aligned = scorer.scoreWindow(sentences, 6.0, 3.0, context, 0, 6000);
        var midTurn = scorer.scoreWindow(sentences, 6.0, 3.0, context, 1000, 6500);

        assertThat(aligned.speakerHeuristicsApplied()).isTrue();
        assertThat(aligned.overall()).isGreaterThan(midTurn.overall());
    }

    @Test
    void speakerHeuristicsDisabledKeepBaseScore() {
        List<SentenceSpan> sentences = List.of(
                new SentenceSpan(0, 2000, "Hello there."),
                new SentenceSpan(2000, 4000, "Second sentence."),
                new SentenceSpan(4000, 7000, "Third line with punctuation!")
        );
        List<SpeakerTurn> turns = List.of(
                new SpeakerTurn("A", 0, 4000),
                new SpeakerTurn("B", 4000, 8000)
        );
        HeuristicScorer.SpeakerContext disabled = new HeuristicScorer.SpeakerContext(turns, false);

        var baseline = scorer.scoreWindow(sentences, 6.0, 3.0, disabled, 0, 6000);
        var withoutContext = scorer.scoreWindow(sentences, 6.0, 3.0, null, 0, 6000);

        assertThat(disabled.speakerHeuristicsApplied()).isFalse();
        assertThat(baseline.overall()).isEqualTo(withoutContext.overall());
    }
}
