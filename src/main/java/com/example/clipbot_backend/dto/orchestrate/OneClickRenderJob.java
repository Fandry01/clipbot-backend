package com.example.clipbot_backend.dto.orchestrate;

import java.util.UUID;

/**
 * Render job representation returned to the client.
 */
public record OneClickRenderJob(UUID clipId, UUID jobId, String status) {
}
