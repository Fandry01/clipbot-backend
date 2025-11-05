package com.example.clipbot_backend.dto.web;

import java.util.UUID;

public record MediaResponse(UUID id, UUID ownerId, String objectKey, Long durationMs, String status, String source,String platform,String externalUrl) {
}
