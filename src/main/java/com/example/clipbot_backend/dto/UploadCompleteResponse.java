package com.example.clipbot_backend.dto;

public record UploadCompleteResponse(
        java.util.UUID mediaId,
        java.util.UUID assetId,
        String objectKey,
        long sizeBytes
) {}
