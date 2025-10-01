package com.example.clipbot_backend.dto.web;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;

public record SegmentBatchItem(@NotNull Long startMs,
                               @NotNull Long endMs,
                               BigDecimal score,
                               Map<String, Object> meta) {}
