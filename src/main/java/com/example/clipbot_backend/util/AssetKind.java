package com.example.clipbot_backend.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumerates the supported asset kinds that can be persisted.
 */
public enum AssetKind {
    MEDIA_RAW,
    MP4,
    WEBM,
    THUMBNAIL,
    SUB_SRT,
    SUB_VTT,
    CLIP_MP4_CLEAN;

    /**
     * Allows tolerant, case-insensitive deserialization from JSON to prevent 400 errors on valid input.
     *
     * @param value incoming value from the request payload.
     * @return matching {@link AssetKind}.
     */
    @JsonCreator
    public static AssetKind fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        for (AssetKind kind : values()) {
            if (kind.name().equalsIgnoreCase(normalized)) {
                return kind;
            }
        }
        if ("MP4_CLEAN".equalsIgnoreCase(normalized)) {
            return CLIP_MP4_CLEAN;
        }
        throw new IllegalArgumentException("Unsupported AssetKind: " + value);
    }

    /**
     * Serializes the enum using its name to keep payloads stable.
     *
     * @return canonical JSON value.
     */
    @JsonValue
    public String toJson() {
        return name();
    }
}
