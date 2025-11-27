package com.example.clipbot_backend.model;

import com.example.clipbot_backend.util.OrchestrationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists idempotent one-click orchestration calls to ensure replayability.
 */
@Entity
@Table(name = "one_click_orchestration", uniqueConstraints = {
        @UniqueConstraint(name = "uk_orchestration_owner_key", columnNames = {"owner_external_subject", "idempotency_key"})
})
public class OneClickOrchestration {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_external_subject", nullable = false, length = 255)
    private String ownerExternalSubject;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OrchestrationStatus status = OrchestrationStatus.IN_PROGRESS;

    @Lob
    @Column(name = "response_payload")
    private String responsePayload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public OneClickOrchestration() {
    }

    public OneClickOrchestration(String ownerExternalSubject, String idempotencyKey) {
        this.ownerExternalSubject = ownerExternalSubject;
        this.idempotencyKey = idempotencyKey;
        this.status = OrchestrationStatus.IN_PROGRESS;
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerExternalSubject() {
        return ownerExternalSubject;
    }

    public void setOwnerExternalSubject(String ownerExternalSubject) {
        this.ownerExternalSubject = ownerExternalSubject;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public OrchestrationStatus getStatus() {
        return status;
    }

    public void setStatus(OrchestrationStatus status) {
        this.status = status;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public void setResponsePayload(String responsePayload) {
        this.responsePayload = responsePayload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
