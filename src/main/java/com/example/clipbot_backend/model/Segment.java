package com.example.clipbot_backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "segment",
        indexes = {
                @Index(name = "idx_segment_media", columnList = "media_id"),
                @Index(name = "idx_segment_media_score", columnList = "media_id, score")
        }
)
@Check(constraints = "end_ms > start_ms")
public class Segment {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_segment_media"))
    private Media media;
    @Column(name = "start_ms", nullable = false)
    private Long startMs;
    @Column(name = "end_ms", nullable = false)
    private Long endMs;
    @Column(name = "score", precision = 6, scale = 3)
    private BigDecimal score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta")
    private Map<String, Object> meta;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Segment() {
    }

    public Segment(Media media, Long startMs, Long endMs) {
        this.media = media;
        this.startMs = startMs;
        this.endMs = endMs;
    }

    public UUID getId() { return id; }
    public Media getMedia() { return media; }
    public void setMedia(Media media) { this.media = media; }
    public long getStartMs() { return startMs; }
    public void setStartMs(long startMs) { this.startMs = startMs; }
    public long getEndMs() { return endMs; }
    public void setEndMs(long endMs) { this.endMs = endMs; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) { this.meta = meta; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
