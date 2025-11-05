package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.ClipResponse;
import com.example.clipbot_backend.dto.web.ProjectCreateRequest;
import com.example.clipbot_backend.dto.web.ProjectMediaLinkRequest;
import com.example.clipbot_backend.dto.web.ProjectMediaLinkResponse;
import com.example.clipbot_backend.dto.web.ProjectResponse;
import com.example.clipbot_backend.model.Clip;
import com.example.clipbot_backend.model.Project;
import com.example.clipbot_backend.model.ProjectMediaLink;
import com.example.clipbot_backend.service.ProjectService;
import com.example.clipbot_backend.util.ClipStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
        return ProjectResponse.from(project);
    }

    /* ================== READ LIST ================== */


    // Ondersteunt ?ownerId=... OF ?ownerExternalSubject=...
    @GetMapping
    @Transactional(readOnly = true)
    public Page<ProjectResponse> listProjects(@RequestParam(defaultValue="0") int page,
                                              @RequestParam(defaultValue="10") int size,
                                              @RequestParam(required = false) String ownerExternalSubject,
                                              @RequestParam(required = false) UUID ownerId) {
        if (page < 0 || size <= 0 || size > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BAD_PAGINATION");
        UUID resolvedOwnerId = resolveOwnerParam(ownerId, ownerExternalSubject);
        Page<Project> p = (ownerExternalSubject != null && !ownerExternalSubject.isBlank())
                ? projectService.listProjectsBySubject(ownerExternalSubject, PageRequest.of(page, size))
                : projectService.listProjects(resolvedOwnerId, PageRequest.of(page, size));

        return p.map(ProjectResponse::from);
    }

    @GetMapping("/{projectId}")
    @Transactional(readOnly = true)
    public ProjectResponse getProject(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) String ownerExternalSubject
    ) {
        UUID resolvedOwnerId = resolveOwnerParam(ownerId, ownerExternalSubject);
        Project project = projectService.getProject(projectId, resolvedOwnerId);
        return ProjectResponse.from(project);
    }

    @PostMapping("/{projectId}/media")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectMediaLinkResponse linkMedia(@PathVariable UUID projectId, @RequestBody LinkReq req) {
        var link = projectService.linkMediaStrict(projectId, req.mediaId());
        var m = link.getMedia();

        return new ProjectMediaLinkResponse(
                m.getId(),
                m.getPlatform(),
                m.getExternalUrl(),
                link.getCreatedAt()
        );
    }

    public record LinkReq(UUID mediaId) {}


    @GetMapping("/{projectId}/media")
    public List<ProjectMediaLinkResponse> listMedia(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) String ownerExternalSubject
    ) {
        UUID resolvedOwnerId = resolveOwnerParam(ownerId, ownerExternalSubject);
        return projectService.listProjectMedia(projectId, resolvedOwnerId)
                .stream()
                .map(l -> new ProjectMediaLinkResponse(
                        l.mediaId(),
                        l.platform(),
                        l.externalUrl(),
                        l.linkedAt()
                ))
                .toList();
    }

    /* ================== CLIPS ================== */

    // Ondersteunt filters + owner bridging
    @GetMapping("/{projectId}/clips")
    public Page<ClipResponse> listClips(
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





}
