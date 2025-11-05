package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.*;
import com.example.clipbot_backend.model.Template;
import com.example.clipbot_backend.repository.AccountRepository;
import com.example.clipbot_backend.repository.ProjectRepository;
import com.example.clipbot_backend.repository.TemplateRepository;
import com.example.clipbot_backend.service.Interfaces.TemplateService;
import com.example.clipbot_backend.util.TemplateMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class TemplateServiceImpl implements TemplateService {

    private final TemplateRepository repo;
    private final ProjectRepository projectRepo;
    private final AccountRepository accountRepo;
    private final TemplateMapper mapper = new TemplateMapper();

    public TemplateServiceImpl(TemplateRepository repo, ProjectRepository projectRepo, AccountRepository accountRepo) {
        this.repo = repo;
        this.projectRepo = projectRepo;
        this.accountRepo = accountRepo;
    }

    @Override
    public TemplateResponse create(TemplateCreateRequest req) {
        if (req.ownerId() == null) throw bad("OWNER_REQUIRED");
        if (req.name() == null || req.name().isBlank()) throw bad("NAME_REQUIRED");

        var ownerRef = accountRepo.getReferenceById(req.ownerId());
        var t = new Template();
        t.setOwner(ownerRef);
        t.setName(req.name().trim());
        t.setJsonConfig(mapper.toJson(req.config()));
        repo.save(t);
        return mapper.toResponse(t);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TemplateResponse> list(UUID ownerId, Pageable pageable) {
        if (ownerId == null) throw bad("OWNER_REQUIRED");
        return repo.findAllByOwnerId(ownerId, pageable).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateResponse get(UUID id, UUID ownerId) {
        var t = repo.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> notFound("TEMPLATE_NOT_FOUND"));
        return mapper.toResponse(t);
    }

    @Override
    public TemplateResponse update(UUID id, TemplateUpdateRequest req) {
        var t = repo.findByIdAndOwnerId(id, req.ownerId())
                .orElseThrow(() -> notFound("TEMPLATE_NOT_FOUND_OR_NOT_OWNED"));
        if (req.name() != null && !req.name().isBlank()) t.setName(req.name().trim());
        if (req.config() != null) t.setJsonConfig(mapper.toJson(req.config()));
        return mapper.toResponse(t);
    }

    @Override
    public void delete(UUID id, UUID ownerId) {
        var t = repo.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> notFound("TEMPLATE_NOT_FOUND_OR_NOT_OWNED"));
        repo.delete(t);
    }

    @Override
    public void applyToProject(UUID projectId, ApplyTemplateRequest req) {
        var p = projectRepo.findById(projectId)
                .orElseThrow(() -> notFound("PROJECT_NOT_FOUND"));
        if (!p.getOwner().getId().equals(req.ownerId())) throw forbidden("PROJECT_NOT_OWNED");
        var t = repo.findByIdAndOwnerId(req.templateId(), req.ownerId())
                .orElseThrow(() -> notFound("TEMPLATE_NOT_FOUND_OR_NOT_OWNED"));
        // simpele apply: project.templateId = t.id
        p.setTemplateId(t.getId());
        projectRepo.save(p);
    }

    @Override
    @Transactional(readOnly = true)
    public AppliedTemplateResponse getApplied(UUID projectId, UUID ownerId) {
        var p = projectRepo.findById(projectId)
                .orElseThrow(() -> notFound("PROJECT_NOT_FOUND"));
        if (!p.getOwner().getId().equals(ownerId)) throw forbidden("PROJECT_NOT_OWNED");
        if (p.getTemplateId() == null) return new AppliedTemplateResponse(null, Map.of());
        var t = repo.findByIdAndOwnerId(p.getTemplateId(), ownerId)
                .orElseThrow(() -> notFound("TEMPLATE_NOT_FOUND"));
        // evt. “resolve” defaults hier samenvoegen:
        var resp = mapper.toResponse(t);
        return new AppliedTemplateResponse(resp.id(), resp.config());
    }

    private ResponseStatusException notFound(String code){
        return new ResponseStatusException(HttpStatus.NOT_FOUND, code);
    }
    private ResponseStatusException bad(String code){
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, code);
    }
    private ResponseStatusException forbidden(String code){
        return new ResponseStatusException(HttpStatus.FORBIDDEN, code);
    }
}
