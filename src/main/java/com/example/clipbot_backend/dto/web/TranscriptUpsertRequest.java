package com.example.clipbot_backend.dto.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record TranscriptUpsertRequest(
        @NotNull UUID mediaId,
        @NotBlank String lang,
        @NotBlank String provider,
        String text,
        Map<String, Object> words
) {
}
