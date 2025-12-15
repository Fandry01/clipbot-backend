package com.example.clipbot_backend.util;

import com.example.clipbot_backend.dto.SentenceSpan;
import com.example.clipbot_backend.dto.SpeakerTurn;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeuristicScorer {
    public record Result(
            double overall,
            double lenScore,
            boolean hasHook,
            boolean hasPayoff,
            double boundaryBonus,
            double speakerBoundaryBonus,
            double speakerTurnBonus,
            double speakerMidPenalty,
            boolean speakerHeuristicsApplied
    ) {
        public Map<String, Object> toMeta() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("overall", overall);
            m.put("lenScore", lenScore);
            m.put("hasHook", hasHook);
            m.put("hasPayoff", hasPayoff);
            m.put("boundaryBonus", boundaryBonus);
            m.put("speakerBoundaryBonus", speakerBoundaryBonus);
            m.put("speakerTurnBonus", speakerTurnBonus);
            m.put("speakerMidPenalty", speakerMidPenalty);
            m.put("speakerHeuristicsApplied", speakerHeuristicsApplied);
            return m;
        }
    }

    // licht uitgebreid
    private static final Set<String> HOOK = Set.of(
            "here's","listen","crazy","secret","tip","watch","big","why","how","mistake","best","warning","truth"
    );
    private static final Set<String> PAYOFF = Set.of(
            "so","therefore","that's why","result","in short","ultimately","the key is","summary","bottom line"
    );

    // minimale bodem zodat niks volledig afvalt
    private static final double LEN_FLOOR = 0.15;   // min score voor lengte
    private static final double MIN_SIGMA = 6.0;    // voorkom te scherpe bel
    private static final double BASE_BOUNDARY = 0.05;
    private static final long TURN_WINDOW_MS = 900;
    private static final long MID_TURN_PENALTY_MS = 1_600;
    private static final double TURN_COUNT_BONUS = 0.06;
    private static final double MID_TURN_PENALTY = 0.08;

    private static final Logger LOGGER = LoggerFactory.getLogger(HeuristicScorer.class);

    public Result scoreWindow(List<SentenceSpan> sents, double targetLenSec, double sigmaSec, SpeakerContext speakerContext, long startMsOverride, long endMsOverride) {
        if (sents == null || sents.isEmpty()) {
            return new Result(0, 0, false, false, 0, 0, 0, 0, false);
        }

        long startMs = startMsOverride;
        long endMs   = endMsOverride;
        double lenSec = Math.max(0.001, (endMs - startMs) / 1000.0);

        // 1) Lengte-score – tolerant
        double sigma = Math.max(MIN_SIGMA, sigmaSec <= 0 ? MIN_SIGMA : sigmaSec);
        double lenScore = cauchy(lenSec, targetLenSec, sigma); // toleranter dan gaussian
        lenScore = Math.max(LEN_FLOOR, lenScore);              // geef bodem

        // 2) Tekst features
        String all = sents.stream().map(SentenceSpan::text).collect(java.util.stream.Collectors.joining(" ")).toLowerCase();
        boolean hasHook   = containsAnyWord(all, HOOK);
        boolean hasPayoff = containsAnyWord(all, PAYOFF);

        // 3) Boundary bonus (netjes begin/eind)
        String trimmed = all.trim();
        boolean endsNeat = trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?");
        boolean startsNeat = Character.isLetterOrDigit(trimmed.isEmpty() ? ' ' : trimmed.charAt(0));
        double boundaryBonus = BASE_BOUNDARY + (endsNeat ? 0.06 : 0) + (startsNeat ? 0.04 : 0);

        // 3b) Speaker-aware heuristieken (alleen wanneer aangezet)
        double speakerBoundaryBonus = 0;
        double speakerTurnBonus = 0;
        double speakerMidPenalty = 0;
        boolean speakerApplied = speakerContext != null && speakerContext.enabled() && speakerContext.hasTurns();
        if (speakerApplied) {
            long nearestStart = speakerContext.distanceToBoundary(startMs);
            long nearestEnd = speakerContext.distanceToBoundary(endMs);
            if (nearestStart >= 0 && nearestStart <= TURN_WINDOW_MS) {
                speakerBoundaryBonus += 0.06 * (1.0 - (nearestStart / (double) TURN_WINDOW_MS));
            } else if (nearestStart > MID_TURN_PENALTY_MS && !startsNeat) {
                speakerMidPenalty += MID_TURN_PENALTY * 0.5;
            }
            if (nearestEnd >= 0 && nearestEnd <= TURN_WINDOW_MS) {
                speakerBoundaryBonus += 0.06 * (1.0 - (nearestEnd / (double) TURN_WINDOW_MS));
            } else if (nearestEnd > MID_TURN_PENALTY_MS && !endsNeat) {
                speakerMidPenalty += MID_TURN_PENALTY * 0.5;
            }

            int turnsInside = speakerContext.countTurnsWithin(startMs, endMs);
            if (turnsInside >= 2 && turnsInside <= 8) {
                speakerTurnBonus += TURN_COUNT_BONUS;
            } else if (turnsInside == 0) {
                speakerMidPenalty += MID_TURN_PENALTY;
            } else if (turnsInside > 10) {
                speakerMidPenalty += MID_TURN_PENALTY * 0.5;
            }
        }

        // 4) Combineer – geef inhoudelijke termen wel gewicht, maar niet allesbepalend
        double base = 0.6 * lenScore
                + (hasHook ? 0.12 : 0)
                + (hasPayoff ? 0.12 : 0)
                + boundaryBonus;

        double speakerDelta = speakerBoundaryBonus + speakerTurnBonus - speakerMidPenalty;
        double overall = base + speakerDelta;

        // zachte clamp en bodem: we willen nooit exact 0
        overall = Math.max(0.05, Math.min(1.0, overall));
        if (speakerApplied && LOGGER.isDebugEnabled()) {
            LOGGER.debug("Speaker scoring applied start={} end={} boundaryBonus={} midPenalty={} turnBonus={} turnsEnabled={}",
                    startMs, endMs, speakerBoundaryBonus, speakerMidPenalty, speakerTurnBonus, speakerApplied);
        }
        return new Result(overall, lenScore, hasHook, hasPayoff, boundaryBonus,
                speakerBoundaryBonus, speakerTurnBonus, speakerMidPenalty, speakerApplied);
    }

    public record SpeakerContext(List<SpeakerTurn> turns, boolean enabled) {
        public boolean hasTurns() { return turns != null && !turns.isEmpty(); }

        public long distanceToBoundary(long timestampMs) {
            if (!hasTurns()) return -1;
            long best = Long.MAX_VALUE;
            for (int i = 1; i < turns.size(); i++) {
                if (!turns.get(i).speaker().equals(turns.get(i - 1).speaker())) {
                    long boundary = turns.get(i).startMs();
                    long dist = Math.abs(timestampMs - boundary);
                    if (dist < best) best = dist;
                }
            }
            return best == Long.MAX_VALUE ? -1 : best;
        }

        public int countTurnsWithin(long startMs, long endMs) {
            if (!hasTurns()) return 0;
            int turnsCount = 0;
            SpeakerTurn prev = null;
            for (SpeakerTurn t : turns) {
                if (t.endMs() <= startMs || t.startMs() >= endMs) continue;
                if (prev != null && !prev.speaker().equals(t.speaker())) {
                    turnsCount++;
                }
                prev = t;
            }
            return turnsCount;
        }
    }

    // Tolerantere bel: 1 / (1 + ((x-mu)/sigma)^2)
    private static double cauchy(double x, double mu, double sigma) {
        double d = (x - mu) / sigma;
        return 1.0 / (1.0 + d * d);
    }

    // match op woordgrenzen, zodat "how" ≠ "show"
    private static boolean containsAnyWord(String t, Set<String> keys) {
        for (String k : keys) {
            String pat = "\\b" + java.util.regex.Pattern.quote(k) + "\\b";
            if (java.util.regex.Pattern.compile(pat).matcher(t).find()) return true;
        }
        return false;
    }
}


