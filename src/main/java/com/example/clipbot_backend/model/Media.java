package com.example.clipbot_backend.model;

import com.example.clipbot_backend.util.MediaStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.util.UUID;

@Entity
public class Media {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_media_owner"))
    private Account owner;

    @Column(name = "object_key", length = 1024)
    private String objectKey;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MediaStatus status = MediaStatus.UPLOADED;

    @Column(name = "source", length = 1024)
    private String source;

    @Column(name = "external_url", length = 2048)
    private String externalUrl;

    @Column(name = "platform", length = 64)
    private String platform;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDate createdAt;

    public Media(UUID id, Account owner, String objectKey, Long durationMs,String source, LocalDate createdAt) {
        this.id = id;
        this.owner = owner;
        this.objectKey = objectKey;
        this.durationMs = durationMs;

        this.source = source;
        this.createdAt = createdAt;
    }
    public Media(){}

    public Media(Account owner, String objectKey ) {
        this.owner = owner;
        this.objectKey = objectKey;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public Account getOwner() {
        return owner;
    }

    public void setOwner(Account owner) {
        this.owner = owner;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDate createdAt) {
        this.createdAt = createdAt;
    }

    public MediaStatus getStatus() {
        return status;
    }

    public void setStatus(MediaStatus status) {
        this.status = status;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl) {
        this.externalUrl = externalUrl;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }
}
