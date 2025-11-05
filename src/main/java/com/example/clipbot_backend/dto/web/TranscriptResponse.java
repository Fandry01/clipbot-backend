package com.example.clipbot_backend.dto.web;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TranscriptResponse(UUID id, UUID mediaId, String lang, String provider, String text,
                                 JsonNode words, Instant createdAt, long version) {
}
