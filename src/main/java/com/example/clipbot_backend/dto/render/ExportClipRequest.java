package com.example.clipbot_backend.dto.render;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ExportClipRequest(
        @NotNull UUID clipId,
        @Valid SubtitleStyle subtitleStyle,
        String profile
) {}
