package com.example.clipbot_backend.dto.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MediaFromUrlRequest(
        UUID ownerId,
        String ownerExternalSubject,
        @NotBlank @Size(max = 2048) String url,
        String source,
        String objectKeyOverride,
        Boolean podcastOrInterview
) {

    @AssertTrue(message = "Either ownerId or ownerExternalSubject is required")
    public boolean hasOwner() {
        return ownerId != null || (ownerExternalSubject != null && !ownerExternalSubject.isBlank());
    }

    @AssertTrue(message = "Provide either ownerId or ownerExternalSubject, not both")
    public boolean onlyOneOwnerProvided() {
        return !(ownerId != null && ownerExternalSubject != null && !ownerExternalSubject.isBlank());
    }
}
