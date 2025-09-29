package com.example.clipbot_backend.service;

import com.example.clipbot_backend.model.Job;
import com.example.clipbot_backend.util.JobType;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface IJobService {
    UUID enqueue(UUID mediaID, JobType type, Map<String, Object> payload);
    Optional<Job> pickOneQueued();
    void markDone(UUID id, Map<String, Object> result);
    void markError(UUID id,String message, Map<String, Object> details);
}

