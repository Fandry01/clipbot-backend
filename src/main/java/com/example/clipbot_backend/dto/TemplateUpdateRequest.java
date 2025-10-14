package com.example.clipbot_backend.dto;

import java.util.Map;
import java.util.UUID;

public record TemplateUpdateRequest(
        UUID ownerId, // ownership check
        String name,
        Map<String,Object> config
) {}
