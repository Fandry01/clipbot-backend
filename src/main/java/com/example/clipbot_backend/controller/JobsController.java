package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.service.JobService;
import com.example.clipbot_backend.util.JobType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/jobs")
public class JobsController {
    private final JobService jobService;
    public JobsController(JobService jobService) { this.jobService = jobService; }

    public record EnqueueReq(UUID mediaId, String type, Map<String,Object> payload) {}
    public record EnqueueRes(UUID jobId) {}

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
}

