package com.example.clipbot_backend.controller;

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
    public ProjectResponse createProject(@Valid @RequestBody ProjectCreateRequest request) {
        Project project = projectService.createProject(request.ownerId(), request.title(), request.templateId());
        return toResponse(project);
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
    public ProjectMediaLinkResponse linkMedia(@PathVariable UUID projectId,
                                              @Valid @RequestBody ProjectMediaLinkRequest request) {
        ProjectMediaLink link = projectService.linkMedia(projectId, request.ownerId(), request.mediaId());
        return toResponse(link);
    }

    @GetMapping("/{projectId}/media")
    public List<ProjectMediaLinkResponse> listMedia(@PathVariable UUID projectId, @RequestParam UUID ownerId) {
        return projectService.listProjectMedia(projectId, ownerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{projectId}/clips")
    public Page<Clip> listClips(@PathVariable UUID projectId,
                                @RequestParam UUID ownerId,
                                @RequestParam(required = false) ClipStatus status,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "10") int size) {
        return projectService.listProjectClips(projectId, ownerId, status, PageRequest.of(page, size));
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(project.getId(), project.getOwner().getId(), project.getTitle(),
                project.getCreatedAt(), project.getTemplateId());
    }

    private ProjectMediaLinkResponse toResponse(ProjectMediaLink link) {
        return new ProjectMediaLinkResponse(link.getId().getProjectId(), link.getId().getMediaId(), link.getCreatedAt());
    }
}
