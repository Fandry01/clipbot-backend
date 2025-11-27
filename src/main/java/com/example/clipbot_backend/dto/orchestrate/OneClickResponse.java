package com.example.clipbot_backend.dto.orchestrate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Response payload emitted by the one-click orchestration flow.
 */
public class OneClickResponse {
    private UUID projectId;
    private UUID mediaId;
    private boolean createdProject;
    private OneClickJob detectJob;
    private OneClickRecommendation recommendations;
    private List<OneClickRenderJob> renderJobs = new ArrayList<>();
    private String thumbnailSource;

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public UUID getMediaId() {
        return mediaId;
    }

    public void setMediaId(UUID mediaId) {
        this.mediaId = mediaId;
    }

    public boolean isCreatedProject() {
        return createdProject;
    }

    public void setCreatedProject(boolean createdProject) {
        this.createdProject = createdProject;
    }

    public OneClickJob getDetectJob() {
        return detectJob;
    }

    public void setDetectJob(OneClickJob detectJob) {
        this.detectJob = detectJob;
    }

    public OneClickRecommendation getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(OneClickRecommendation recommendations) {
        this.recommendations = recommendations;
    }

    public List<OneClickRenderJob> getRenderJobs() {
        return renderJobs;
    }

    public void setRenderJobs(List<OneClickRenderJob> renderJobs) {
        this.renderJobs = renderJobs == null ? List.of() : renderJobs;
    }

    public String getThumbnailSource() {
        return thumbnailSource;
    }

    public void setThumbnailSource(String thumbnailSource) {
        this.thumbnailSource = thumbnailSource;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final OneClickResponse instance = new OneClickResponse();

        public Builder projectId(UUID projectId) {
            instance.setProjectId(projectId);
            return this;
        }

        public Builder mediaId(UUID mediaId) {
            instance.setMediaId(mediaId);
            return this;
        }

        public Builder createdProject(boolean createdProject) {
            instance.setCreatedProject(createdProject);
            return this;
        }

        public Builder detectJob(OneClickJob detectJob) {
            instance.setDetectJob(detectJob);
            return this;
        }

        public Builder recommendations(OneClickRecommendation recommendations) {
            instance.setRecommendations(recommendations);
            return this;
        }

        public Builder renderJobs(List<OneClickRenderJob> renderJobs) {
            instance.setRenderJobs(renderJobs);
            return this;
        }

        public Builder thumbnailSource(String thumbnailSource) {
            instance.setThumbnailSource(thumbnailSource);
            return this;
        }

        public OneClickResponse build() {
            Objects.requireNonNull(instance.getProjectId(), "projectId");
            Objects.requireNonNull(instance.getMediaId(), "mediaId");
            Objects.requireNonNull(instance.getDetectJob(), "detectJob");
            Objects.requireNonNull(instance.getRecommendations(), "recommendations");
            return instance;
        }
    }
}
