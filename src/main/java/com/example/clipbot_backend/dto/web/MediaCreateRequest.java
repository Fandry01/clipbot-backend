package com.example.clipbot_backend.dto.web;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MediaCreateRequest(@NotNull UUID ownerId, @NotBlank String objectKey, String source) {
}
