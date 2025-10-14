package com.example.clipbot_backend.service.Interfaces;

import com.example.clipbot_backend.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TemplateService {
    TemplateResponse create(TemplateCreateRequest req);
    Page<TemplateResponse> list(UUID ownerId, Pageable pageable);
    TemplateResponse get(UUID id, UUID ownerId);
    TemplateResponse update(UUID id, TemplateUpdateRequest req);
    void delete(UUID id, UUID ownerId);

    // Apply to project
    void applyToProject(UUID projectId, ApplyTemplateRequest req);
    AppliedTemplateResponse getApplied(UUID projectId, UUID ownerId);
}
