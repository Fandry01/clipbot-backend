package com.example.clipbot_backend.model;

import com.example.clipbot_backend.util.AssetKind;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
public class Asset {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, foreignKey = @ForeignKey(name = "fk_asset_owner"))
    private Account owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private AssetKind kind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_media_id", foreignKey = @ForeignKey(name = "fk_asset_media"))
    private Media relatedMedia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_clip_id",
            foreignKey = @ForeignKey(name = "fk_asset_clip"))
    private Clip relatedClip;

    @Column(name = "object_key", nullable = false, length = 1024)
    private String objectKey;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum", length = 128)
    private String checkSum;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;


    protected Asset() {}

    public Asset(Account owner, AssetKind kind, String objectKey, long sizeBytes) {
        this.owner = owner;
        this.kind = kind;
        this.objectKey = objectKey;
        this.sizeBytes = sizeBytes;
    }

    public UUID getId()
    { return id; }
    public Account getOwner()
    { return owner; }
    public void setOwner(Account owner)
    { this.owner = owner; }
    public AssetKind getKind()
    { return kind; }
    public void setKind(AssetKind kind)
    { this.kind = kind; }
    public Media getRelatedMedia()
    { return relatedMedia; }
    public void setRelatedMedia(Media relatedMedia)
    { this.relatedMedia = relatedMedia; }
    public Clip getRelatedClip()
    { return relatedClip; }
    public void setRelatedClip(Clip relatedClip)
    { this.relatedClip = relatedClip; }
    public String getObjectKey()
    { return objectKey; }
    public void setObjectKey(String objectKey)
    { this.objectKey = objectKey; }
    public long getSizeBytes()
    { return sizeBytes; }
    public void setSizeBytes(long sizeBytes)
    { this.sizeBytes = sizeBytes; }
    public String getChecksum()
    { return checkSum; }
    public void setChecksum(String checkSum)
    { this.checkSum = checkSum; }
    public Instant getCreatedAt()
    { return createdAt; }
    public long getVersion()
    { return version; }
}
