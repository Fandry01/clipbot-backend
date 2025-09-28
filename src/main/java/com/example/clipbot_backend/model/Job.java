package com.example.clipbot_backend.model;


import com.example.clipbot_backend.util.JobStatus;
import com.example.clipbot_backend.util.JobType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "job",
        indexes = {
                @Index(name = "idx_job_status_created", columnList = "status, created_at"),
                @Index(name = "idx_job_media", columnList = "media_id")
        }
)
public class Job {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id",
            foreignKey = @ForeignKey(name = "fk_job_media"))
    private Media media;
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private JobType type;

    private JobStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    private Map<String, Object>  payload;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result")
    private Map<String, Object> result;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @PreUpdate
    void touch() { this.updatedAt = Instant.now(); }

    protected Job() {}

    public Job(JobType type) { this.type = type; }

    // getters/setters ...

    public Media getMedia() {
        return media;
    }

    public void setMedia(Media media) {
        this.media = media;
    }

    public JobType getType() {
        return type;
    }

    public void setType(JobType type) {
        this.type = type;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
