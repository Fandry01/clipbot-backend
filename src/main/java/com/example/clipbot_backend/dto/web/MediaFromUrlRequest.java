package com.example.clipbot_backend.dto.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MediaFromUrlRequest(
        @NotNull UUID ownerId,
        @NotBlank @Size(max = 2048) String url,
        String source
) {
}
