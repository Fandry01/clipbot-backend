package com.example.clipbot_backend.util;

import com.example.clipbot_backend.dto.SentenceSpan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HeuristicScorer {
    public record Result(
            double overall,
            double lenScore,
            boolean hasHook,
            boolean hasPayoff,
            double boundaryBonus
    ) {
        public Map<String, Object> toMeta() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("overall", overall);
            m.put("lenScore", lenScore);
            m.put("hasHook", hasHook);
            m.put("hasPayoff", hasPayoff);
            m.put("boundaryBonus", boundaryBonus);
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

    public Result scoreWindow(List<SentenceSpan> sents, double targetLenSec, double sigmaSec) {
        if (sents == null || sents.isEmpty()) {
            return new Result(0, 0, false, false, 0);
        }

        long startMs = sents.getFirst().startMs();
        long endMs   = sents.getLast().endMs();
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

        // 4) Combineer – geef inhoudelijke termen wel gewicht, maar niet allesbepalend
        double overall = 0.6 * lenScore
                + (hasHook ? 0.12 : 0)
                + (hasPayoff ? 0.12 : 0)
                + boundaryBonus;

        // zachte clamp en bodem: we willen nooit exact 0
        overall = Math.max(0.05, Math.min(1.0, overall));
        return new Result(overall, lenScore, hasHook, hasPayoff, boundaryBonus);
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


