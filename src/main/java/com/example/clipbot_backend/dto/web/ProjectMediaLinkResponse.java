package com.example.clipbot_backend.dto.web;

import java.time.Instant;
import java.util.UUID;

public record ProjectMediaLinkResponse(
        UUID projectId,
        UUID mediaId,
        Instant createdAt
) {
}
