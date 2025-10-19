package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.web.ProjectCreateRequest;
import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Clip;
import com.example.clipbot_backend.model.Project;
import com.example.clipbot_backend.model.ProjectMediaLink;
import com.example.clipbot_backend.repository.ClipRepository;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.ProjectMediaRepository;
import com.example.clipbot_backend.repository.ProjectRepository;
import com.example.clipbot_backend.service.AccountService;
import com.example.clipbot_backend.util.ClipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectMediaRepository projectMediaRepository;
    private final AccountService accountService;
    private final MediaRepository mediaRepository;
    private final ClipRepository clipRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectMediaRepository projectMediaRepository,
            AccountService accountService,
            MediaRepository mediaRepository,
            ClipRepository clipRepository
    ) {
        this.projectRepository = projectRepository;
        this.projectMediaRepository = projectMediaRepository;
        this.accountService = accountService;
        this.mediaRepository = mediaRepository;
        this.clipRepository = clipRepository;
    }

    /* ---------- CREATE ---------- */

    @Transactional
    public Project createProject(Account owner, String title, UUID templateId) {
        var project = new Project(owner, title, templateId);
        return projectRepository.save(project);
    }

    // Overload voor bestaande callers met UUID (eventueel tijdelijk aanhouden)
    @Transactional
    public Project createProject(UUID ownerId, String title, UUID templateId) {
        var owner = accountService.getByIdOrThrow(ownerId);
        return createProject(owner, title, templateId);
    }

    // Handig voor controller met ProjectCreateRequest
    @Transactional
    public Project createProject(ProjectCreateRequest req) {
        var owner = resolveOwner(req);
        return createProject(owner, req.title(), req.templateId());
    }

    /* ---------- READ ---------- */

    public Page<Project> listProjects(UUID ownerId, Pageable pageable) {
        var owner = accountService.getByIdOrThrow(ownerId);
        return projectRepository.findByOwnerOrderByCreatedAtDesc(owner, pageable);
    }

    // Variant via externalSubject – fijn als je UI dat meestuurt i.p.v. UUID
    public Page<Project> listProjectsBySubject(String ownerExternalSubject, Pageable pageable) {
        var owner = accountService.getByExternalSubjectOrThrow(ownerExternalSubject);
        return projectRepository.findByOwnerOrderByCreatedAtDesc(owner, pageable);
    }

    public Project getProject(UUID projectId, UUID ownerId) {
        var owner = accountService.getByIdOrThrow(ownerId);
        return ensureProjectOwnedBy(projectId, owner);
    }

    /* ---------- MEDIA-LINK ---------- */

    @Transactional
    public ProjectMediaLink linkMedia(UUID projectId, UUID ownerId, UUID mediaId) {
        var owner = accountService.getByIdOrThrow(ownerId);
        var project = ensureProjectOwnedBy(projectId, owner);

        var media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));

        if (!media.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "MEDIA_NOT_OWNED");
        }
        if (projectMediaRepository.existsByProjectAndMedia(project, media)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MEDIA_ALREADY_LINKED");
        }
        var link = new ProjectMediaLink(project, media);
        return projectMediaRepository.save(link);
    }

    public List<ProjectMediaLink> listProjectMedia(UUID projectId, UUID ownerId) {
        var owner = accountService.getByIdOrThrow(ownerId);
        var project = ensureProjectOwnedBy(projectId, owner);
        return projectMediaRepository.findByProject(project);
    }

    /* ---------- CLIPS ---------- */

    public Page<Clip> listProjectClips(UUID projectId, UUID ownerId, ClipStatus status, Pageable pageable) {
        var owner = accountService.getByIdOrThrow(ownerId);
        var project = ensureProjectOwnedBy(projectId, owner);

        // MVP: eerst media → dan clips
        var mediaList = projectMediaRepository.findMediaByProject(project);
        if (mediaList.isEmpty()) return Page.empty(pageable);

        if (status != null) {
            return clipRepository.findByMediaInAndStatusOrderByCreatedAtDesc(mediaList, status, pageable);
        }
        return clipRepository.findByMediaInOrderByCreatedAtDesc(mediaList, pageable);
    }

    /* ---------- HELPERS ---------- */

    private Project ensureProjectOwnedBy(UUID projectId, Account owner) {
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND"));
        if (!project.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PROJECT_NOT_OWNED");
        }
        return project;
    }

    private Account resolveOwner(ProjectCreateRequest req) {
        if (req.ownerId() != null) {
            return accountService.getByIdOrThrow(req.ownerId());
        }
        if (req.ownerExternalSubject() != null && !req.ownerExternalSubject().isBlank()) {
            return accountService.getByExternalSubjectOrThrow(req.ownerExternalSubject());
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OWNER_REQUIRED");
    }

    // In ProjectService
    public UUID getOwnerIdByExternalSubject(String externalSubject) {
        return accountService.getByExternalSubjectOrThrow(externalSubject).getId();
    }

}
