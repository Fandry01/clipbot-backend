package com.example.clipbot_backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "project_media",indexes = {
        @Index(name="idx_pm_project", columnList="project_id"),
        @Index(name="idx_pm_media",   columnList="media_id")
})
public class ProjectMediaLink {
    @EmbeddedId
    private ProjectMediaId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("projectId")
    @JoinColumn(name = "project_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_project_media_project"))
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("mediaId")
    @JoinColumn(name = "media_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_project_media_media"))
    private Media media;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProjectMediaLink() {
    }

    public ProjectMediaLink(Project project, Media media) {
        this.project = project;
        this.media = media;
        this.id = new ProjectMediaId(project.getId(), media.getId());
    }

    public ProjectMediaId getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public Media getMedia() {
        return media;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
