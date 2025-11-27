package com.example.clipbot_backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project")
public class Project {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_project_owner"))
    private Account owner;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "normalized_source_url", length = 2048)
    private String normalizedSourceUrl;

    @Column(name = "thumbnail_url", length = 2048)
    private String thumbnailUrl;

    @Column(name = "template_id", nullable = true)
    private UUID templateId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Project() {
    }

    public Project(Account owner, String title, UUID templateId) {
        this.owner = owner;
        this.title = title;
        this.templateId = templateId;
    }

    public Project(Account owner, String title, UUID templateId, String normalizedSourceUrl) {
        this(owner, title, templateId);
        this.normalizedSourceUrl = normalizedSourceUrl;
    }

    public UUID getId() {
        return id;
    }

    public Account getOwner() {
        return owner;
    }

    public void setOwner(Account owner) {
        this.owner = owner;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public void setTemplateId(UUID templateId) {
        this.templateId = templateId;
    }

    public String getNormalizedSourceUrl() {
        return normalizedSourceUrl;
    }

    public void setNormalizedSourceUrl(String normalizedSourceUrl) {
        this.normalizedSourceUrl = normalizedSourceUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

