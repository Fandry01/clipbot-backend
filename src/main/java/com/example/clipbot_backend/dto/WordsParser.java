package com.example.clipbot_backend.dto;

import com.example.clipbot_backend.model.Transcript;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public final class WordsParser {
    private WordsParser() {}

    public static final class WordAdapter {
        public final String text;
        public final long startMs;
        public final long endMs;
        public final Double confidence;
        public WordAdapter(String text, long startMs, long endMs, Double confidence) {
            this.text = text == null ? "" : text;
            this.startMs = Math.max(0, startMs);
            this.endMs   = Math.max(this.startMs, endMs);
            this.confidence = confidence;
        }
    }

    public static List<WordAdapter> extract(Transcript tr) {
        if (tr == null || tr.getWords() == null) return List.of();
        JsonNode root = tr.getWords();
        List<WordAdapter> out;

        // 1) schema v1: { items: [ { startMs,endMs,text,confidence? } ] }
        out = parseItemsArray(root.path("items"));
        if (!out.isEmpty()) return out;

        // 2) FW verbose: { segments:[ { words:[ {start,end,word|text,confidence?} ] } ] }
        out = parseFwSegments(root.path("segments"));
        if (!out.isEmpty()) return out;

        // 3a) vlakke words-array: { words:[ {startMs,endMs,text} ] }
        out = parseItemsArray(root.path("words"));
        if (!out.isEmpty()) return out;

        // 3b) top-level array als laatste fallback
        if (root.isArray()) {
            out = parseItemsArray(root);
            if (!out.isEmpty()) return out;
        }

        return List.of();
    }

    /* ---------- helpers ---------- */

    private static List<WordAdapter> parseItemsArray(JsonNode arr) {
        if (!arr.isArray()) return List.of();
        List<WordAdapter> out = new ArrayList<>(arr.size());
        for (JsonNode n : arr) {
            long s = pickMs(n.get("startMs"), n.get("startSec"));
            long e = pickMs(n.get("endMs"),   n.get("endSec"));
            String text = firstText(n, "text", "word");
            Double conf = asDouble(n.get("confidence"), n.get("conf"));
            if (text != null && !text.isBlank() && e >= s) {
                out.add(new WordAdapter(text, s, e, conf));
            }
        }
        return out;
    }

    private static List<WordAdapter> parseFwSegments(JsonNode segs) {
        if (!segs.isArray()) return List.of();
        List<WordAdapter> out = new ArrayList<>();
        for (JsonNode seg : segs) {
            JsonNode words = seg.path("words");
            if (!words.isArray()) continue;
            for (JsonNode w : words) {
                long s = asMsFromSeconds(w.get("start"));
                long e = asMsFromSeconds(w.get("end"));
                String text = firstText(w, "word", "text");
                Double conf = asDouble(w.get("confidence"), w.get("conf"));
                if (text != null && !text.isBlank() && e >= s) {
                    out.add(new WordAdapter(text, s, e, conf));
                }
            }
        }
        return out;
    }

    private static String firstText(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v != null && !v.isNull()) return v.asText("");
        }
        return "";
    }

    private static long pickMs(JsonNode msNode, JsonNode secNode) {
        if (msNode != null && !msNode.isNull()) return asLong(msNode);
        if (secNode != null && !secNode.isNull()) return asMsFromSeconds(secNode);
        return 0L;
    }

    private static long asLong(JsonNode n) {
        if (n == null || n.isNull()) return 0L;
        if (n.isNumber()) return n.longValue();
        try { return Long.parseLong(n.asText("0")); } catch (Exception ignore) { return 0L; }
    }

    private static long asMsFromSeconds(JsonNode n) {
        if (n == null || n.isNull()) return 0L;
        double d = n.isNumber() ? n.doubleValue() : safeDouble(n.asText("0"));
        return Math.round(d * 1000.0);
    }

    private static Double asDouble(JsonNode... nodes) {
        for (JsonNode n : nodes) {
            if (n == null || n.isNull()) continue;
            if (n.isNumber()) return n.doubleValue();
            try { return Double.parseDouble(n.asText()); } catch (Exception ignore) {}
        }
        return null;
    }

    private static double safeDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0.0; }
    }
}

