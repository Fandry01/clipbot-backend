package com.example.clipbot_backend.dto;

import java.util.Map;
import java.util.UUID;

public record AppliedTemplateResponse(
        UUID templateId,
        Map<String,Object> resolved // eventueel samengevoegde defaults
) {}
