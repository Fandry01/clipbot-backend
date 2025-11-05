package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.SentenceSpan;
import com.example.clipbot_backend.dto.SilenceEvent;
import com.example.clipbot_backend.util.HeuristicScorer;

import java.util.*;

public class ClipAssembler {

    public static final class Window {
        public final int startIdx, endIdx;
        public final long startMs, endMs;
        public final double score;
        public final Map<String,Object> scoreComponents;

        public Window(int startIdx, int endIdx, long startMs, long endMs, double score, Map<String,Object> scoreComponents) {
            this.startIdx = startIdx; this.endIdx = endIdx;
            this.startMs = startMs; this.endMs = endMs;
            this.score = score; this.scoreComponents = scoreComponents;
        }
    }

    private final HeuristicScorer scorer = new HeuristicScorer();

    public List<Window> windows(List<SentenceSpan> sentences,
                                List<SilenceEvent> silences,
                                long minMs, long maxMs, long snapThreshMs,
                                double targetLenSec, double sigmaSec,
                                int maxCandidates) {

        List<Window> out = new ArrayList<>();
        if (sentences.isEmpty()) return out;

        for (int i = 0; i <sentences.size(); i++) {
            for (int j = i; j<sentences.size(); j++) {
                long s = sentences.get(i).startMs(), e = sentences.get(j).endMs(), d = e - s;
                if (d < minMs) continue;
                if (d > maxMs) break;

                // Snap aan stilte
                long sSnap = snapLeft(s, silences, snapThreshMs);
                long eSnap = snapRight(e, silences, snapThreshMs);
                if (sSnap>=0) s = sSnap;
                if (eSnap>=0) e = eSnap;

                var comp = scorer.scoreWindow(sentences.subList(i, j+1), targetLenSec, sigmaSec);
                if (comp.overall() <= 0.0) continue;

                out.add(new Window(i,j,s,e,comp.overall(), comp.toMeta()));

                if (out.size() > maxCandidates * 10) break;
            }
        }

        // sort & dedup
        out.sort(Comparator.comparingDouble(w -> -w.score));
        List<Window> dedup = new ArrayList<>();
        boolean[] used = new boolean[sentences.size()];
        for (var w : out) {
            boolean overlap=false;
            for (int k=w.startIdx;k<=w.endIdx;k++){ if (used[k]) { overlap=true; break; } }
            if (!overlap) {
                for (int k=w.startIdx;k<=w.endIdx;k++) used[k]=true;
                dedup.add(w);
                if (dedup.size()>=maxCandidates) break;
            }
        }
        return dedup;
    }

    private long snapLeft(long ms, List<SilenceEvent> silences, long thresh){
        long best=-1,distBest=Long.MAX_VALUE;
        for (var s: silences){ long dist=Math.abs(ms - s.endMs()); if (dist<=thresh && dist<distBest){distBest=dist; best=s.endMs();}}
        return best;
    }
    private long snapRight(long ms, List<SilenceEvent> silences, long thresh){
        long best=-1,distBest=Long.MAX_VALUE;
        for (var s: silences){ long dist=Math.abs(ms - s.startMs()); if (dist<=thresh && dist<distBest){distBest=dist; best=s.startMs();}}
        return best;
    }
}
