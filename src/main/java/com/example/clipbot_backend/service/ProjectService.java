package com.example.clipbot_backend.service;

import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Clip;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Project;
import com.example.clipbot_backend.model.ProjectMediaLink;
import com.example.clipbot_backend.repository.AccountRepository;
import com.example.clipbot_backend.repository.ClipRepository;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.ProjectMediaRepository;
import com.example.clipbot_backend.repository.ProjectRepository;
import com.example.clipbot_backend.util.ClipStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectMediaRepository projectMediaRepository;
    private final AccountRepository accountRepository;
    private final MediaRepository mediaRepository;
    private final ClipRepository clipRepository;

    public ProjectService(ProjectRepository projectRepository,
                          ProjectMediaRepository projectMediaRepository,
                          AccountRepository accountRepository,
                          MediaRepository mediaRepository,
                          ClipRepository clipRepository) {
        this.projectRepository = projectRepository;
        this.projectMediaRepository = projectMediaRepository;
        this.accountRepository = accountRepository;
        this.mediaRepository = mediaRepository;
        this.clipRepository = clipRepository;
    }

    @Transactional
    public Project createProject(UUID ownerId, String title, UUID templateId) {
        Account owner = accountRepository.findById(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OWNER_NOT_FOUND"));
        var project = new Project(owner, title, templateId);
        return projectRepository.save(project);
    }

    public Page<Project> listProjects(UUID ownerId, Pageable pageable) {
        Account owner = accountRepository.findById(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OWNER_NOT_FOUND"));
        return projectRepository.findByOwnerOrderByCreatedAtDesc(owner, pageable);
    }

    public Project getProject(UUID projectId, UUID ownerId) {
        return ensureProjectOwnedBy(projectId, ownerId);
    }

    @Transactional
    public ProjectMediaLink linkMedia(UUID projectId, UUID ownerId, UUID mediaId) {
        Project project = ensureProjectOwnedBy(projectId, ownerId);
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));
        if (!media.getOwner().getId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
        if (projectMediaRepository.existsByProjectAndMedia(project, media)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MEDIA_ALREADY_LINKED");
        }
        var link = new ProjectMediaLink(project, media);
        return projectMediaRepository.save(link);
    }

    public List<ProjectMediaLink> listProjectMedia(UUID projectId, UUID ownerId) {
        Project project = ensureProjectOwnedBy(projectId, ownerId);
        return projectMediaRepository.findByProject(project);
    }

    public Page<Clip> listProjectClips(UUID projectId, UUID ownerId, ClipStatus status, Pageable pageable) {
        Project project = ensureProjectOwnedBy(projectId, ownerId);
        List<Media> mediaList = projectMediaRepository.findMediaByProject(project);
        if (mediaList.isEmpty()) {
            return Page.empty(pageable);
        }
        if (status != null) {
            return clipRepository.findByMediaInAndStatusOrderByCreatedAtDesc(mediaList, status, pageable);
        }
        return clipRepository.findByMediaInOrderByCreatedAtDesc(mediaList, pageable);
    }

    private Project ensureProjectOwnedBy(UUID projectId, UUID ownerId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND"));
        if (!project.getOwner().getId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
        return project;
    }
}
