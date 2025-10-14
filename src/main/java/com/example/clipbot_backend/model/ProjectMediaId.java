package com.example.clipbot_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ProjectMediaId implements Serializable {
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "media_id", nullable = false)
    private UUID mediaId;

    public ProjectMediaId() {
    }

    public ProjectMediaId(UUID projectId, UUID mediaId) {
        this.projectId = projectId;
        this.mediaId = mediaId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getMediaId() {
        return mediaId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectMediaId that = (ProjectMediaId) o;
        return Objects.equals(projectId, that.projectId) && Objects.equals(mediaId, that.mediaId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectId, mediaId);
    }
}
