package com.example.clipbot_backend.dto.media;

import java.util.UUID;

/**
 * Minimal response for media creation from URL.
 */
public record CreateFromUrlResponse(UUID mediaId) {
}
