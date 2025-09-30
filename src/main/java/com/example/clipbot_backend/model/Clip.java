package com.example.clipbot_backend.model;

import com.example.clipbot_backend.util.ClipStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
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
    private ClipStatus status;

    @Column(name = "title", length = 255)
    private String title;
    @Column(name = "caption_srt_key", length = 1024)
    private String captionSrtKey;
    @Column(name = "caption_vtt_key", length = 1024)
    private String captionVttKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta")
    private Map<String, Object> meta;

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



}
