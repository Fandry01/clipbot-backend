package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.service.JobService;
import com.example.clipbot_backend.util.JobType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/jobs")
public class JobsController {
    private final JobService jobService;
    public JobsController(JobService jobService) { this.jobService = jobService; }

    public static record EnqueueReq(UUID mediaId, String type, Map<String,Object> payload) {}
    public static record EnqueueRes(UUID jobId) {}

    @PostMapping("/enqueue")
    public EnqueueRes enqueue(@RequestBody EnqueueReq req) {
        JobType t = JobType.valueOf(req.type().toUpperCase());
        UUID id = jobService.enqueue(req.mediaId(), t, req.payload()==null?Map.of():req.payload());
        return new EnqueueRes(id);
    }
}
