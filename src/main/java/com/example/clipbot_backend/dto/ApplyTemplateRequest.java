package com.example.clipbot_backend.dto;

import java.util.UUID;

public record ApplyTemplateRequest(
        UUID ownerId,
        UUID templateId
) {}
