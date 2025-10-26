package com.example.clipbot_backend.dto;

import com.example.clipbot_backend.model.Asset;
import com.example.clipbot_backend.util.AssetKind;

import java.time.Instant;
import java.util.UUID;

public record AssetResponse
        (UUID id, AssetKind kind, String objectKey, Long size, Instant createdAt, UUID relatedClipId,
         UUID relatedMediaId){
    public static AssetResponse from(Asset a) {
        return new AssetResponse(
                a.getId(),
                a.getKind(),
                a.getObjectKey(),
                a.getSizeBytes(),
                a.getCreatedAt(),
                a.getRelatedClip() != null ? a.getRelatedClip().getId() : null,
                a.getRelatedMedia() != null ? a.getRelatedMedia().getId() : null
        );
    }
}
