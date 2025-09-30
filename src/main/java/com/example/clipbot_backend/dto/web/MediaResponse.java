package com.example.clipbot_backend.dto.web;

import java.util.UUID;

public record MediaResponse(UUID id, UUID ownerId, String objectKey, long durationMs, String status, String source) {
}
