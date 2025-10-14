package com.example.clipbot_backend.dto.web;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ClipResponse(
        UUID id,
        UUID mediaId,
        UUID sourceSegmentId,
        long startMs,
        long endMs,
        String status,
        String title,
        String captionSrtKey,
        String captionVttKey,
        Map<String, Object> meta,
        Instant createdAt,
        long version
) {
}
