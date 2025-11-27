package com.example.clipbot_backend.dto.orchestrate;

import java.util.UUID;

/**
 * Minimal job view used in the one-click response.
 */
public record OneClickJob(UUID jobId, String status) {
}
