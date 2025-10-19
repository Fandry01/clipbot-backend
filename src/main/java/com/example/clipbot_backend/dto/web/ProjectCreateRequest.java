package com.example.clipbot_backend.dto.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProjectCreateRequest(
        UUID ownerId,
        String ownerExternalSubject,
        @NotBlank String title,
        UUID templateId
) {
    @AssertTrue(message = "Either ownerId or ownerExternalSubject is required")
    public boolean isOwnerProvided() {
        return ownerId != null || (ownerExternalSubject != null && !ownerExternalSubject.isBlank());
    }
}
