package com.example.clipbot_backend.dto;

import java.util.Objects;

/**
 * Patch container for partial project updates.
 */
public class ProjectPatch {
    private final String title;
    private final String thumbnailUrl;
    private final String normalizedSourceUrl;

    private ProjectPatch(Builder builder) {
        this.title = builder.title;
        this.thumbnailUrl = builder.thumbnailUrl;
        this.normalizedSourceUrl = builder.normalizedSourceUrl;
    }

    public String title() {
        return title;
    }

    public String thumbnailUrl() {
        return thumbnailUrl;
    }

    public String normalizedSourceUrl() {
        return normalizedSourceUrl;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String title;
        private String thumbnailUrl;
        private String normalizedSourceUrl;

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder thumbnailUrl(String thumbnailUrl) {
            this.thumbnailUrl = thumbnailUrl;
            return this;
        }

        public Builder normalizedSourceUrl(String normalizedSourceUrl) {
            this.normalizedSourceUrl = normalizedSourceUrl;
            return this;
        }

        public ProjectPatch build() {
            if (title == null && thumbnailUrl == null && normalizedSourceUrl == null) {
                throw new IllegalStateException("ProjectPatch requires at least one field to be set");
            }
            return new ProjectPatch(this);
        }
    }

    @Override
    public String toString() {
        return "ProjectPatch{" +
                "title='" + title + '\'' +
                ", thumbnailUrl='" + thumbnailUrl + '\'' +
                ", normalizedSourceUrl='" + normalizedSourceUrl + '\'' +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, thumbnailUrl, normalizedSourceUrl);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ProjectPatch other)) return false;
        return Objects.equals(title, other.title)
                && Objects.equals(thumbnailUrl, other.thumbnailUrl)
                && Objects.equals(normalizedSourceUrl, other.normalizedSourceUrl);
    }
}
