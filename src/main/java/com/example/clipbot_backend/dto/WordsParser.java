package com.example.clipbot_backend.dto;

import com.example.clipbot_backend.model.Transcript;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class WordsParser {
    private WordsParser(){}

    public static final class WordAdapter {
        public final String text;
        public final long startMs;
        public final long endMs;
        public final Double confidence;

        public WordAdapter(String text, long startMs, long endMs, Double confidence) {
            this.text = text;
            this.startMs = startMs;
            this.endMs = endMs;
            this.confidence = confidence;
        }
    }
        @SuppressWarnings("unchecked")
        public static List<WordAdapter> extract(Transcript tr) {
            Object w = tr.getWords();                    // jouw @JdbcTypeCode(SqlTypes.JSON)
            if (w == null) return List.of();

            List<Map<String, Object>> items = null;

            if (w instanceof List) {
                items = (List<Map<String, Object>>) w;
            } else if (w instanceof Map<?, ?> m) {
                Object maybeList = ((Map<String, Object>) m).get("items");
                if (!(maybeList instanceof List)) {
                    maybeList = ((Map<String, Object>) m).get("words");
                }
                if (maybeList instanceof List) {
                    items = (List<Map<String, Object>>) maybeList;
                }
            }
            if (items == null) return List.of();

            List<WordAdapter> out = new ArrayList<>(items.size());
            for (Map<String, Object> wm : items) {
                String text = str(wm.getOrDefault("text", wm.getOrDefault("word", "")));
                long sMs = pickMs(wm.get("startMs"), wm.get("startSec"));
                long eMs = pickMs(wm.get("endMs"), wm.get("endSec"));
                Double conf = numD(wm.getOrDefault("confidence", wm.get("conf")));
                if (text != null && !text.isBlank() && eMs >= sMs) {
                    out.add(new WordAdapter(text, sMs, eMs, conf));
                }
            }
            return out;
        }

        private static long pickMs(Object ms, Object sec) {
            if (ms != null) return numL(ms);
            if (sec != null) return Math.round(numD(sec) * 1000.0);
            return 0L;
        }

        private static String str(Object o) {
            return (o == null) ? null : String.valueOf(o);
        }

        private static long numL(Object o) {
            if (o instanceof Number n) return n.longValue();
            try {
                return Long.parseLong(String.valueOf(o));
            } catch (Exception e) {
                return 0L;
            }
        }

        private static double numD(Object o) {
            if (o instanceof Number n) return n.doubleValue();
            try {
                return Double.parseDouble(String.valueOf(o));
            } catch (Exception e) {
                return 0.0;
            }
        }
    }