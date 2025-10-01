package com.example.clipbot_backend.dto;

import java.util.UUID;

public record TranscriptResponse(UUID id, UUID mediaId, String lang, String provider, String text) {
}
