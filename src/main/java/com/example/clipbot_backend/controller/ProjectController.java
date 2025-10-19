package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.web.ProjectCreateRequest;
import com.example.clipbot_backend.dto.web.ProjectMediaLinkRequest;
import com.example.clipbot_backend.model.Clip;
import com.example.clipbot_backend.model.Project;
import com.example.clipbot_backend.model.ProjectMediaLink;
import com.example.clipbot_backend.service.ProjectService;
import com.example.clipbot_backend.util.ClipStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /* ================== CREATE ================== */

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse createProject(@Valid @RequestBody ProjectCreateRequest request) {
        // Service resolve’t owner (ownerId of ownerExternalSubject)
        Project project = projectService.createProject(request);
        return toResponse(project);
    }

    /* ================== READ LIST ================== */

    // Ondersteunt ?ownerId=... OF ?ownerExternalSubject=...
    @GetMapping
    public Page<ProjectResponse> listProjects(
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) String ownerExternalSubject,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<Project> p = (ownerExternalSubject != null && !ownerExternalSubject.isBlank())
                ? projectService.listProjectsBySubject(ownerExternalSubject, PageRequest.of(page, size))
                : projectService.listProjects(ownerId, PageRequest.of(page, size));
        return p.map(this::toResponse);
    }

    /* ================== READ ONE ================== */

    // Ondersteunt ?ownerId=... OF ?ownerExternalSubject=...
    @GetMapping("/{projectId}")
    public ProjectResponse getProject(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) String ownerExternalSubject
    ) {
        UUID resolvedOwnerId = resolveOwnerParam(ownerId, ownerExternalSubject);
        Project project = projectService.getProject(projectId, resolvedOwnerId);
        return toResponse(project);
    }

    /* ================== MEDIA LINKING ================== */

    @PostMapping("/{projectId}/media")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectMediaLinkResponse linkMedia(
            @PathVariable UUID projectId,
            @Valid @RequestBody ProjectMediaLinkRequest request // bevat ownerId OF ownerExternalSubject + mediaId
    ) {
        UUID resolvedOwnerId = resolveOwnerParam(request.ownerId(), request.ownerExternalSubject());
        ProjectMediaLink link = projectService.linkMedia(projectId, resolvedOwnerId, request.mediaId());
        return toResponse(link);
    }

    @GetMapping("/{projectId}/media")
    public List<ProjectMediaLinkResponse> listMedia(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) String ownerExternalSubject
    ) {
        UUID resolvedOwnerId = resolveOwnerParam(ownerId, ownerExternalSubject);
        return projectService.listProjectMedia(projectId, resolvedOwnerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /* ================== CLIPS ================== */

    // Ondersteunt filters + owner bridging
    @GetMapping("/{projectId}/clips")
    public Page<Clip> listClips(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) String ownerExternalSubject,
            @RequestParam(required = false) ClipStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        UUID resolvedOwnerId = resolveOwnerParam(ownerId, ownerExternalSubject);
        return projectService.listProjectClips(projectId, resolvedOwnerId, status, PageRequest.of(page, size));
    }

    /* ================== HELPERS ================== */

    private UUID resolveOwnerParam(UUID ownerId, String ownerExternalSubject) {
        // Bridge: geef voorkeur aan externalSubject (veiliger / klaar voor auth),
        // val terug op ownerId voor backwards compatibility.
        if (ownerExternalSubject != null && !ownerExternalSubject.isBlank()) {
            // ProjectService/AccountService expose’t een helper om subject -> Account te mappen.
            // We willen hier alleen UUID retourneren; een lichte façade in ProjectService kan ook.
            return projectService
                    .getOwnerIdByExternalSubject(ownerExternalSubject); // voeg deze convenience toe in je service
        }
        return ownerId; // kan null zijn -> service gooit dan OWNER_REQUIRED
    }

    private ProjectResponse toResponse(Project p) {
        return new ProjectResponse(
                p.getId(),
                p.getOwner().getId(),
                p.getTitle(),
                p.getCreatedAt(),
                p.getTemplateId()
        );
    }

    private ProjectMediaLinkResponse toResponse(ProjectMediaLink link) {
        return new ProjectMediaLinkResponse(
                link.getId().getProjectId(),
                link.getId().getMediaId(),
                link.getCreatedAt()
        );
    }

    /* ================== DTOs (API view) ================== */

    public record ProjectResponse(
            UUID id,
            UUID ownerId,
            String title,
            Instant createdAt,
            UUID templateId
    ) {}

    public record ProjectMediaLinkResponse(
            UUID projectId,
            UUID mediaId,
            Instant createdAt
    ) {}
}
