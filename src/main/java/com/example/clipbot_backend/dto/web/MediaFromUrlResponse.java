package com.example.clipbot_backend.dto.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MediaFromUrlResponse(
        UUID mediaId,
        String status,
        String platform,
        Long durationMs,
        String objectKey
) {
}
