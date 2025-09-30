package com.example.clipbot_backend.dto.web;

public record UploadInitRequest(
        String ownerExternalSubject,
        String keyPrefix,       // bv: "users/{sub}/raw/"
        String originalFilename // optioneel: client filename
) {}

