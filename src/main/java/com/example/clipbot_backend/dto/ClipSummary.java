package com.example.clipbot_backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lightweight summary for recommended clips.
 *
 * @param id          clip identifier.
 * @param startMs     start offset in milliseconds.
 * @param endMs       end offset in milliseconds.
 * @param score       recommendation score.
 * @param status      current clip status string.
 * @param profileHash hash for the applied render profile.
 */
public record ClipSummary(UUID id,
                          long startMs,
                          long endMs,
                          BigDecimal score,
                          String status,
                          String profileHash) {
}
