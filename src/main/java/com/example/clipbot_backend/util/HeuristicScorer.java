package com.example.clipbot_backend.util;

import com.example.clipbot_backend.dto.SentenceSpan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HeuristicScorer {
    public record Result(double overall, double lenScore, boolean hasHook, boolean hasPayoff, double boundaryBonus) {

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
    private final Set<String> hook   = Set.of("here's","listen","crazy","secret","tip","watch","big","why","how","mistake","best");
    private final Set<String> payoff = Set.of("so","therefore","that's why","result","in short","ultimately","the key is");

    public Result scoreWindow(List<SentenceSpan> sents, double targetLenSec, double sigmaSec) {
        if (sents.isEmpty()) return new Result(0,0,false,false,0);
        String all = sents.stream().map(SentenceSpan::text).reduce("", (a,b)->a+" "+b).toLowerCase();

        double lenSec = (sents.getLast().endMs() - sents.getFirst().startMs())/1000.0;
        double lenScore = gaussian(lenSec, targetLenSec, sigmaSec);

        boolean hasHook = containsAny(all, hook);
        boolean hasPayoff = containsAny(all, payoff);
        double boundaryBonus = 0.10;

        double overall = clamp(0.6*lenScore + (hasHook?0.15:0) + (hasPayoff?0.15:0) + boundaryBonus, 0, 1);
        return new Result(overall, lenScore, hasHook, hasPayoff, boundaryBonus);
    }

    private boolean containsAny(String t, Set<String> keys){ for (var k:keys) if (t.contains(k)) return true; return false; }
    private double gaussian(double x,double mu,double sigma){ double d=(x-mu)/sigma; return Math.exp(-0.5*d*d); }
    private double clamp(double v,double a,double b){ return Math.max(a, Math.min(b,v)); }
}

