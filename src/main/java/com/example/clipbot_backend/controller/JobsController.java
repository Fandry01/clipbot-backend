package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.repository.JobRepository;
import com.example.clipbot_backend.service.JobService;
import com.example.clipbot_backend.util.JobType;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/jobs")
public class JobsController {
    private final JobService jobService;
    private final JobRepository jobRepository;
    public JobsController(JobService jobService, JobRepository jobRepository) { this.jobService = jobService;
        this.jobRepository = jobRepository;
    }

    public record EnqueueReq(UUID mediaId, String type, Map<String,Object> payload) {}
    public record EnqueueRes(UUID jobId) {}
    public record JobRes(
            UUID id,
            String type,
            String status,
            int attempts,
            UUID mediaId,
            Map<String,Object> payload,
            Map<String,Object> result,
            Instant createdAt,
            Instant updatedAt
    ) {}

    @PostMapping("/enqueue")
    public EnqueueRes enqueue(@RequestBody EnqueueReq req) {
        if (req == null || req.type() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TYPE_REQUIRED");
        JobType t;
        try { t = JobType.valueOf(req.type().trim().toUpperCase()); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_JOB_TYPE"); }
        UUID id = jobService.enqueue(req.mediaId(), t, req.payload() == null ? Map.of() : req.payload());
        return new EnqueueRes(id);
    }
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public JobRes get(@PathVariable UUID id) {
        var j = jobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND"));
        return new JobRes(
                j.getId(),
                j.getType().name(),
                j.getStatus().name(),
                j.getAttempts(),
                j.getMedia() != null ? j.getMedia().getId() : null,
                j.getPayload(),
                j.getResult(),
                j.getCreatedAt(),
                j.getUpdatedAt()
        );
    }
}

