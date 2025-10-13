package com.example.clipbot_backend.service.metadata;

import com.example.clipbot_backend.util.MediaPlatform;

import java.util.Objects;

public record MetadataResult(
        MediaPlatform platform,
        String url,
        String title,
        String author,
        Long durationSec,
        String thumbnail
) {

    public static MetadataResult empty(MediaPlatform platform, String url) {
        return new MetadataResult(platform, url, null, null, null, null);
    }

    public MetadataResult merge(MetadataResult other) {
        if (other == null) {
            return this;
        }
        return new MetadataResult(
                platform != null ? platform : other.platform(),
                url != null ? url : other.url(),
                firstNonNull(title, other.title()),
                firstNonNull(author, other.author()),
                firstNonNull(durationSec, other.durationSec()),
                firstNonNull(thumbnail, other.thumbnail())
        );
    }

    public boolean hasAnyData() {
        return title != null || author != null || durationSec != null || thumbnail != null;
    }

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }

    @Override
    public String toString() {
        return "MetadataResult{" +
                "platform=" + platform +
                ", url='" + url + '\'' +
                ", title='" + title + '\'' +
                ", author='" + author + '\'' +
                ", durationSec=" + durationSec +
                ", thumbnail='" + thumbnail + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MetadataResult that)) return false;
        return platform == that.platform && Objects.equals(url, that.url) && Objects.equals(title, that.title) && Objects.equals(author, that.author) && Objects.equals(durationSec, that.durationSec) && Objects.equals(thumbnail, that.thumbnail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(platform, url, title, author, durationSec, thumbnail);
    }
}
