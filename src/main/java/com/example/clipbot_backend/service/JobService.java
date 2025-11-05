package com.example.clipbot_backend.service;

import com.example.clipbot_backend.model.Job;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.repository.JobRepository;
import com.example.clipbot_backend.util.JobStatus;
import com.example.clipbot_backend.util.JobType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.annotation.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class JobService {
    private final JobRepository jobRepo;
    private final ObjectMapper mapper;

    public JobService(JobRepository jobRepo, ObjectMapper mapper) {
        this.jobRepo = jobRepo;
        this.mapper = mapper;
    }

    @Transactional
    public UUID enqueue(@Nullable UUID mediaID, JobType type, @Nullable Map<String, Object> payload) {
        var j = new Job(type);
        j.setStatus(JobStatus.QUEUED);
        j.setPayload(payload == null ? Map.of() : payload);
        if (mediaID != null) {
            Media ref = new Media();
            ref.setId(mediaID);
            j.setMedia(ref);
        }
        jobRepo.save(j);
        return j.getId();
    }

    /** Optioneel: unieke enqueue op dedupKey (zie repo + DDL onderaan). */
    @Transactional
    public UUID enqueueUnique(@Nullable UUID mediaId, JobType type, String dedupKey, Map<String,Object> payload) {
        return jobRepo.findQueuedOrRunningByDedupKey(dedupKey)
                .map(Job::getId)
                .orElseGet(() -> {
                    Job j = new Job(type);
                    j.setStatus(JobStatus.QUEUED);
                    j.setPayload(payload == null ? Map.of() : payload);
                    j.setDedupKey(dedupKey); // <-- zorg dat Job.dedupKey bestaat (varchar)
                    if (mediaId != null) { Media m = new Media(); m.setId(mediaId); j.setMedia(m); }
                    jobRepo.save(j);
                    return j.getId();
                });
    }

    @Transactional
    public Optional<Job> pickOneQueued() {
        return jobRepo.selectOneQueuedIdForUpdate()
                .flatMap(id -> jobRepo.markRunning(id) == 1 ? jobRepo.findById(id) : Optional.empty());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDone(UUID id, @Nullable Map<String,Object> result) {
        try {
            String json = mapper.writeValueAsString(result == null ? Map.of() : result);
            jobRepo.markDone(id, json);
        } catch (Exception e) {
            throw new RuntimeException("Serialize result JSON failed", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markError(UUID id, @Nullable String message, @Nullable Map<String,Object> details) {
        try {
            Map<String,Object> payload = new HashMap<>(details == null ? Map.of() : details);
            payload.putIfAbsent("error", message == null ? "unknown" : message);
            String json = mapper.writeValueAsString(payload);
            jobRepo.markError(id, json);
        } catch (Exception e) {
            throw new RuntimeException("Serialize error JSON failed", e);
        }
    }
}
