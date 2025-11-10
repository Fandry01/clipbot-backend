package com.example.clipbot_backend.dto.web;

import com.example.clipbot_backend.model.ProjectMediaLink;

import java.time.Instant;
import java.util.UUID;

public record ProjectMediaLinkResponse(
        UUID projectId,
        UUID mediaId,
        String platform,
        String externalUrl,
        Instant linkedAt
) {
    public static ProjectMediaLinkResponse from(ProjectMediaLink link) {
        return new ProjectMediaLinkResponse(
                link.getId().getProjectId(),
                link.getId().getMediaId(),
                link.getMedia().getExternalUrl(),
                link.getMedia().getPlatform(),
                link.getCreatedAt()
        );
    }
}
