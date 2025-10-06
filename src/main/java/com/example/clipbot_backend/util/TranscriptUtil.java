package com.example.clipbot_backend.util;

import com.example.clipbot_backend.dto.SentenceSpan;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToLongFunction;

public final class TranscriptUtil {
    private TranscriptUtil(){}

    public static <T> List<SentenceSpan> toSentences(
            List<T> words,
            Function<T, String> getText,
            ToLongFunction<T> getStartMs,
            ToLongFunction<T> getEndMs
    ) {
        List<SentenceSpan> out = new ArrayList<>();
        List<T> buf = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        for (T w : words) {
            buf.add(w);
            String t = getText.apply(w);
            if (t != null && !t.isBlank()) {
                sb.append(t).append(" ");
                if (t.matches(".*[.!?]$") && buf.size() >= 4) {
                    out.add(toSpan(buf, sb, getStartMs, getEndMs));
                    buf.clear();
                    sb.setLength(0);
                }
            }
        }
        if (!buf.isEmpty()) {
            out.add(toSpan(buf, sb, getStartMs, getEndMs));
        }
        return out;
    }

    private static <T> SentenceSpan toSpan(
            List<T> buf, StringBuilder sb,
            ToLongFunction<T> getStartMs,
            ToLongFunction<T> getEndMs
    ) {
        long s = getStartMs.applyAsLong(buf.get(0));
        long e = getEndMs.applyAsLong(buf.get(buf.size()-1));
        return new SentenceSpan(s, e, sb.toString().trim());
    }
}
