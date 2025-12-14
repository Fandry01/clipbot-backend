package com.example.clipbot_backend.dto.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Ingestverzoek voor een mediabestand vanaf een externe URL.
 * <p>
 * De boolean {@code podcastOrInterview} wordt door de frontend gezet wanneer het om een
 * gesprek met meerdere sprekers gaat; de backend gebruikt dit om de multi-speaker
 * diarization route (GPT-4o) te kiezen.
 * <p>
 * Voor eigenaarschap kan een interne {@code ownerId} of een externe {@code ownerExternalSubject}
 * worden meegegeven; exact één van beide is verplicht.
 */
public record MediaFromUrlRequest(
        UUID ownerId,
        String ownerExternalSubject,
        @NotBlank @Size(max = 2048) String url,
        String source,
        String objectKeyOverride,
        boolean podcastOrInterview
) {
    @AssertTrue(message = "Either ownerId or ownerExternalSubject is required")
    public boolean hasOwner() {
        return ownerId != null || (ownerExternalSubject != null && !ownerExternalSubject.isBlank());
    }
    @AssertTrue(message = "Provide either ownerId or ownerExternalSubject, not both")
    public boolean notBothOwners() {
        return !(ownerId != null && ownerExternalSubject != null && !ownerExternalSubject.isBlank());
    }
}
