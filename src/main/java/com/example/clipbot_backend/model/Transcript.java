package com.example.clipbot_backend.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
public class Transcript {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_transcript_media"))
    private Media media;
    @Column(name = "lang", nullable = false, length = 16)
    private String lang;
    @Column(name = "provider", nullable = false, length = 64)
    private String provider;
    @Column(name = "text", columnDefinition = "text")
    private String text;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="words", columnDefinition="jsonb")
    private JsonNode words;
    // array
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name="created_at", nullable=false, updatable=false)
    private Instant createdAt;


    public Transcript() {}

    public Transcript(Media media, String lang, String provider) {
        this.media = media;
        this.lang = lang;
        this.provider = provider;
    }
    public UUID getId() { return id; }
    public Media getMedia() { return media; }
    public void setMedia(Media media) { this.media = media; }
    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getText() { return text; }

    public JsonNode getWords() {
        return words;
    }

    public void setWords(JsonNode words) {
        this.words = words;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setText(String text) { this.text = text; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }


}
