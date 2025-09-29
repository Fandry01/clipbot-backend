package com.example.clipbot_backend.dto;

import java.util.Map;

public record SegmentDTO(long startMs, long endMs, java.math.BigDecimal score, Map<String,Object> meta) {
}
