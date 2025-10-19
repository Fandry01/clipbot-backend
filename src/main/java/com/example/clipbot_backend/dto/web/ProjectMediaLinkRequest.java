package com.example.clipbot_backend.dto.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProjectMediaLinkRequest(
        UUID ownerId,
        String ownerExternalSubject,
        @NotNull UUID mediaId
) {
    @AssertTrue(message = "Either ownerId or ownerExternalSubject is required")
    public boolean isOwnerProvided() {
        return ownerId != null || (ownerExternalSubject != null && !ownerExternalSubject.isBlank());
    }
}
