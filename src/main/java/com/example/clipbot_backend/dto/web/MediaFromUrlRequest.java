package com.example.clipbot_backend.dto.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MediaFromUrlRequest(
        String ownerId,
        String ownerExternalSubject,
        @NotBlank @Size(max = 2048) String url,
        String source,
        String objectKeyOverride,
        Boolean podcastOrInterview
) {

    @AssertTrue(message = "Either ownerId or ownerExternalSubject is required")
    public boolean hasOwner() {
        return hasText(ownerId) || hasText(ownerExternalSubject);
    }

    @AssertTrue(message = "Provide either ownerId or ownerExternalSubject, not both")
    public boolean onlyOneOwnerProvided() {
        return !(hasText(ownerId) && hasText(ownerExternalSubject));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
