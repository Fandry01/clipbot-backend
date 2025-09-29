package com.example.clipbot_backend.repository;

import com.example.clipbot_backend.model.Job;
import com.example.clipbot_backend.util.JobStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {
    long countByStatus(JobStatus status);

    // -------- Safe pick pattern (Postgres) ----------

    // 1) Selecteer 1 job met SKIP LOCKED
    @Query(value = """
        SELECT id FROM job
        WHERE status = 'QUEUED'
        ORDER BY created_at
        FOR UPDATE SKIP LOCKED
        LIMIT 1
        """, nativeQuery = true)
    Optional<Job> selectOneQueuedIdForUpdate();

    // 2) Markeer RUNNING
    @Modifying
    @Transactional
    @Query(value = "UPDATE job SET status = 'RUNNING', updated_at = now(), attempts = attempts + 1 WHERE id = :id", nativeQuery = true)
    int markRunning(UUID id);

    // 3) Handige helpers
    @Modifying @Transactional
    @Query(value = "UPDATE job SET status = 'DONE', updated_at = now(), result = CAST(:resultJson AS jsonb) WHERE id = :id", nativeQuery = true)
    int markDone(UUID id, String resultJson);

    @Modifying @Transactional
    @Query(value = "UPDATE job SET status = 'ERROR', updated_at = now(), result = CAST(:errorJson AS jsonb) WHERE id = :id", nativeQuery = true)
    int markError(UUID id, String errorJson);
}


