package com.example.clipbot_backend.service;

import com.example.clipbot_backend.model.Job;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.repository.JobRepository;
import com.example.clipbot_backend.util.JobStatus;
import com.example.clipbot_backend.util.JobType;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class JobService{
    private final JobRepository jobRepo;

    public JobService(JobRepository jobRepo) {
        this.jobRepo = jobRepo;
    }


    @Transactional
    public UUID enqueue(UUID mediaID, JobType type, Map<String, Object> payload) {
        var j = new Job(type);
        j.setStatus(JobStatus.QUEUED);
        j.setPayload(payload);
        j.setCreatedAt(Instant.now());
        j.setUpdatedAt(Instant.now());
        if(mediaID != null) {
            var m = new Media();
            m.setId(mediaID);
            j.setMedia(m);
        }
        jobRepo.save(j);
        return j.getId();
    }

    @Transactional
    public Optional<Job> pickOneQueued() {
        return jobRepo.selectOneQueuedIdForUpdate()
                .flatMap(id ->{
                    if(jobRepo.markRunning(id) == 1){
                        return jobRepo.findById(id);
                    }
                    return Optional.empty();
                });
    }


    @Transactional
    public void markDone(UUID id, Map<String, Object> result){
        jobRepo.markDone(id,result == null ? "{}" : JsonMapper.builder().build().valueToTree(result).toString());
    }

    @Transactional
    public void markError(UUID id, String message, Map<String, Object> details){
        var payload = details == null ? new HashMap<String, Object>() : new HashMap<>(details);
        payload.put("error", message == null ? "unknown" : message);
        jobRepo.markError( id, JsonMapper.builder().build().valueToTree(payload).toString());
    }
}
