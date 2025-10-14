package com.example.clipbot_backend.dto.web;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProjectMediaLinkRequest(
        @NotNull UUID ownerId,
        @NotNull UUID mediaId
) {
}
