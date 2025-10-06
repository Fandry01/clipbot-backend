package com.example.clipbot_backend.dto;

public record DetectionParams(long minDurationMs,
                              long maxDurationMs,
                              int maxCandidates,
                              double silenceNoiseDb,
                              double silenceMinDurSec,
                              long snapThresholdMs,
                              double targetLenSec,
                              double lenSigmaSec) {
}
