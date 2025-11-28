package com.example.clipbot_backend.service.thumbnail;

import com.example.clipbot_backend.model.Project;
import com.example.clipbot_backend.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages project thumbnails, allowing uploads to defer thumbnail assignment until the first render completes.
 */
@Service
public class ThumbnailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThumbnailService.class);
    private final ProjectRepository projectRepository;
    private final Set<UUID> pendingFallbacks = ConcurrentHashMap.newKeySet();

    public ThumbnailService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    /**
     * Registers a project for deferred thumbnail assignment when the first clip render finishes.
     *
     * @param projectId project identifier to mark.
     */
    public void registerFirstRenderFallback(UUID projectId) {
        if (projectId == null) {
            return;
        }
        pendingFallbacks.add(projectId);
        LOGGER.info("ThumbnailService fallback registered projectId={}", projectId);
    }

    /**
     * Applies a thumbnail to the project if it is awaiting a fallback and currently has no thumbnail.
     *
     * @param project      project entity to mutate when needed.
     * @param thumbnailUrl thumbnail URL or object key produced by the render flow.
     */
    @Transactional
    public void applyFallbackIfEligible(Project project, String thumbnailUrl) {
        if (project == null || thumbnailUrl == null || thumbnailUrl.isBlank()) {
            return;
        }
        if (!pendingFallbacks.contains(project.getId())) {
            return;
        }
        if (project.getThumbnailUrl() != null && !project.getThumbnailUrl().isBlank()) {
            pendingFallbacks.remove(project.getId());
            return;
        }
        project.setThumbnailUrl(thumbnailUrl);
        projectRepository.save(project);
        pendingFallbacks.remove(project.getId());
        LOGGER.info("ThumbnailService fallback applied projectId={} thumbnail={}", project.getId(), thumbnailUrl);
    }
}
