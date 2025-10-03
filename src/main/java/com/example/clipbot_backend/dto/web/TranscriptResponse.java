package com.example.clipbot_backend.dto.web;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TranscriptResponse(UUID id, UUID mediaId, String lang, String provider, String text,
                                 Map<String, Object> words, Instant createdAt,  long version) {
}
