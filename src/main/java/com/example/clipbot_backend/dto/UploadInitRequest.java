package com.example.clipbot_backend.dto;

public record UploadInitRequest(
        String ownerExternalSubject,
        String keyPrefix,       // bv: "users/{sub}/raw/"
        String originalFilename // optioneel: client filename
) {}

