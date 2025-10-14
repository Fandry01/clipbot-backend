package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.web.ClipResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody ProjectCreateRequest request) {
        Project project = projectService.createProject(request.ownerId(), request.title(), request.templateId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(project));
    }

    @GetMapping
    public Page<ProjectResponse> listProjects(@RequestParam UUID ownerId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return projectService.listProjects(ownerId, PageRequest.of(page, size)).map(this::toResponse);
    }

    @GetMapping("/{projectId}")
    public ProjectResponse getProject(@PathVariable UUID projectId, @RequestParam UUID ownerId) {
        Project project = projectService.getProject(projectId, ownerId);
        return toResponse(project);
    }

    @PostMapping("/{projectId}/media")
    public ResponseEntity<ProjectMediaLinkResponse> linkMedia(@PathVariable UUID projectId,
                                                              @Valid @RequestBody ProjectMediaLinkRequest request) {
        ProjectMediaLink link = projectService.linkMedia(projectId, request.ownerId(), request.mediaId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(link));
    }

    @GetMapping("/{projectId}/media")
    public List<ProjectMediaLinkResponse> listMedia(@PathVariable UUID projectId, @RequestParam UUID ownerId) {
        return projectService.listProjectMedia(projectId, ownerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{projectId}/clips")
    public Page<ClipResponse> listClips(@PathVariable UUID projectId,
                                        @RequestParam UUID ownerId,
                                        @RequestParam(required = false) ClipStatus status,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        return projectService.listProjectClips(projectId, ownerId, status, PageRequest.of(page, size))
                .map(this::toResponse);
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(project.getId(), project.getOwner().getId(), project.getTitle(),
                project.getCreatedAt(), project.getTemplateId());
    }

    private ProjectMediaLinkResponse toResponse(ProjectMediaLink link) {
        return new ProjectMediaLinkResponse(link.getId().getProjectId(), link.getId().getMediaId(), link.getCreatedAt());
    }

    private ClipResponse toResponse(Clip clip) {
        var segment = clip.getSourceSegment();
        UUID segmentId = segment != null ? segment.getId() : null;
        return new ClipResponse(
                clip.getId(),
                clip.getMedia().getId(),
                segmentId,
                clip.getStartMs(),
                clip.getEndMs(),
                clip.getStatus() != null ? clip.getStatus().name() : null,
                clip.getTitle(),
                clip.getCaptionSrtKey(),
                clip.getCaptionVttKey(),
                clip.getMeta(),
                clip.getCreatedAt(),
                clip.getVersion()
        );
    }
}
