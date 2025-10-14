package com.example.clipbot_backend.controller;




import com.example.clipbot_backend.dto.TemplateCreateRequest;
import com.example.clipbot_backend.dto.TemplateResponse;
import com.example.clipbot_backend.dto.TemplateUpdateRequest;
import com.example.clipbot_backend.service.Interfaces.TemplateService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;

    }

    @PostMapping
    public ResponseEntity<TemplateResponse> create(@RequestBody TemplateCreateRequest req){
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.create(req));
    }

    @GetMapping
    public Page<TemplateResponse> list(@RequestParam UUID ownerId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "10") int size){
        return templateService.list(ownerId, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public TemplateResponse get(@PathVariable UUID id, @RequestParam UUID ownerId){
        return templateService.get(id, ownerId);
    }

    @PatchMapping("/{id}")
    public TemplateResponse update(@PathVariable UUID id, @RequestBody TemplateUpdateRequest req){
        return templateService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestParam UUID ownerId){
        templateService.delete(id, ownerId);
    }
}
