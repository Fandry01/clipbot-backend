package com.example.clipbot_backend.selector;

import java.util.Set;

/**
 * Window features derived from a transcript and segment slice.
 *
 * @param startMs        start offset in milliseconds (inclusive).
 * @param endMs          end offset in milliseconds (exclusive).
 * @param speechDensity  ratio of spoken audio versus window duration.
 * @param avgConfidence  average word confidence for spoken tokens.
 * @param textEnergy     normalized textual energy (words per second + emphasis).
 * @param silencePenalty penalty for silences where {@code 1.0} means mostly silent.
 * @param keywords       matched keywords that describe this window.
 */
public record Window(long startMs,
                     long endMs,
                     double speechDensity,
                     double avgConfidence,
                     double textEnergy,
                     double silencePenalty,
                     Set<String> keywords) {
}
