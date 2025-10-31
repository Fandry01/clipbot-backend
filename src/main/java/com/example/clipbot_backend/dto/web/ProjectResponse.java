package com.example.clipbot_backend.dto.web;

import com.example.clipbot_backend.model.Project;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        UUID ownerId,
        String title,
        String ownerExternalSubject,
        UUID templateId,
        Instant createdAt
) {
    public static ProjectResponse from(Project p) {
        var o = p.getOwner();
        return new ProjectResponse(
                p.getId(),
                o.getId(),
                p.getTitle(),
                o.getExternalSubject(),
                p.getTemplateId(),
                p.getCreatedAt()
        );
    }
}
