package com.example.clipbot_backend.dto;

import com.example.clipbot_backend.model.Clip;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ClipResponse(
        UUID id,
        UUID mediaId,
        long startMs,
        long endMs,
        String title,
        Map<String,Object> meta,
        String status,
        String captionSrtKey,
        String captionVttKey,
        Instant createdAt
) {
    public static ClipResponse from(Clip c) {
        return new ClipResponse(
                c.getId(),
                c.getMedia().getId(),
                c.getStartMs(),
                c.getEndMs(),
                c.getTitle(),
                c.getMeta(),
                c.getStatus().name(),
                c.getCaptionSrtKey(),
                c.getCaptionVttKey(),
                c.getCreatedAt()
        );
    }
}
