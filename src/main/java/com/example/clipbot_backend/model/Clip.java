package com.example.clipbot_backend.model;

import com.example.clipbot_backend.util.ClipStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "clip", uniqueConstraints = {
        @UniqueConstraint(name = "ux_clip_media_range_profile",
                columnNames = {"media_id", "start_ms", "end_ms", "profile_hash"})
})
public class Clip {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_clip_media"))
    private Media media;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_segment_id",
            foreignKey = @ForeignKey(name = "fk_clip_segment"))
    private Segment sourceSegment;

    @Column(name = "start_ms", nullable = false)
    private long startMs;
    @Column(name = "end_ms", nullable = false)
    private long endMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ClipStatus status = ClipStatus.QUEUED;

    @Column(name = "title", length = 255)
    private String title;
    @Column(name = "caption_srt_key", length = 1024)
    private String captionSrtKey;
    @Column(name = "caption_vtt_key", length = 1024)
    private String captionVttKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta")
    private Map<String, Object> meta;

    @Column(name = "profile_hash", nullable = false, columnDefinition = "text")
    private String profileHash = "";

    @Column(name = "score", precision = 6, scale = 3)
    private BigDecimal score;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;


    protected Clip() {}

    public Clip(Media media, long startMs, long endMs) {
        this.media = media;
        this.startMs = startMs;
        this.endMs = endMs;
    }

    public UUID getId() { return id; }
    public Media getMedia() { return media; }
    public void setMedia(Media media) { this.media = media; }
    public Segment getSourceSegment() { return sourceSegment; }
    public void setSourceSegment(Segment sourceSegment) { this.sourceSegment = sourceSegment; }
    public long getStartMs() { return startMs; }
    public void setStartMs(long startMs) { this.startMs = startMs; }
    public long getEndMs() { return endMs; }
    public void setEndMs(long endMs) { this.endMs = endMs; }
    public ClipStatus getStatus() { return status; }
    public void setStatus(ClipStatus status) { this.status = status; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCaptionSrtKey() { return captionSrtKey; }
    public void setCaptionSrtKey(String captionSrtKey) { this.captionSrtKey = captionSrtKey; }
    public String getCaptionVttKey() { return captionVttKey; }
    public void setCaptionVttKey(String captionVttKey) { this.captionVttKey = captionVttKey; }
    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) { this.meta = meta; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }

    /**
     * Returns the render profile hash that differentiates clips with identical ranges.
     *
     * @return profile hash, never {@code null}.
     */
    public String getProfileHash() { return profileHash; }

    /**
     * Updates the profile hash used for deduplicating clip ranges.
     *
     * @param profileHash profile hash value, must not be {@code null}.
     */
    public void setProfileHash(String profileHash) { this.profileHash = profileHash; }

    /**
     * Returns the recommendation score assigned to the clip.
     *
     * @return score or {@code null} when no score is known.
     */
    public BigDecimal getScore() { return score; }

    /**
     * Stores the recommendation score for the clip.
     *
     * @param score score value or {@code null}.
     */
    public void setScore(BigDecimal score) { this.score = score; }
}
