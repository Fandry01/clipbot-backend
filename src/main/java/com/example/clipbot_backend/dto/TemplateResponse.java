package com.example.clipbot_backend.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TemplateResponse(
        UUID id,
        UUID ownerId,
        String name,
        Map<String,Object> config,
        Instant createdAt,
        Instant updatedAt
) {}
