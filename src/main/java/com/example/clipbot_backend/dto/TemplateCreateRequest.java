package com.example.clipbot_backend.dto;

import java.util.Map;
import java.util.UUID;

public record TemplateCreateRequest(
        UUID ownerId,
        String name,
        Map<String,Object> config // je volledige template-config (layout, captions, aiFlags, etc.)
) {}
