package com.example.clipbot_backend.model;

import com.example.clipbot_backend.util.MediaStatus;
import com.example.clipbot_backend.util.SpeakerMode;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.beans.Transient;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.example.clipbot_backend.util.SpeakerMode.*;

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

    @Column(name = "is_multi_speaker")
    private Boolean isMultiSpeaker;

    @Column(name = "speaker_count_detected")
    private Integer speakerCountDetected;

    @Enumerated(EnumType.STRING)
    private SpeakerMode speakerMode;

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

    public Boolean getMultiSpeaker() {
        return isMultiSpeaker;
    }

    public void setMultiSpeaker(Boolean multiSpeaker) {
        isMultiSpeaker = multiSpeaker;
    }

    public Integer getSpeakerCountDetected() {
        return speakerCountDetected;
    }

    public void setSpeakerCountDetected(Integer speakerCountDetected) {
        this.speakerCountDetected = speakerCountDetected;
    }

    public SpeakerMode getSpeakerMode() {
        return speakerMode;
    }

    public void setSpeakerMode(SpeakerMode speakerMode) {
        this.speakerMode = speakerMode;
    }

    @Transient
    public boolean isMultiSpeakerEffective() {
        SpeakerMode mode = this.speakerMode != null ? this.speakerMode : SpeakerMode.AUTO;
        return switch (mode) {
            case MULTI  -> true;                                  // Interview/Podcast
            case SINGLE -> false;                                 // Monologue
            case AUTO   -> (speakerCountDetected != null && speakerCountDetected > 1);
        };
    }
}

