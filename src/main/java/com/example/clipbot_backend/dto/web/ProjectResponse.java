package com.example.clipbot_backend.dto.web;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        UUID ownerId,
        String title,
        Instant createdAt,
        UUID templateId
) {
}
