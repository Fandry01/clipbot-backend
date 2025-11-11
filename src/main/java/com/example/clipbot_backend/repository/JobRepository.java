package com.example.clipbot_backend.repository;

import com.example.clipbot_backend.model.Job;
import com.example.clipbot_backend.util.JobStatus;
import com.example.clipbot_backend.util.JobType;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {
    long countByStatus(JobStatus status);

    @Query(value = """
        SELECT id FROM job
        WHERE status = 'QUEUED'
        ORDER BY created_at
        FOR UPDATE SKIP LOCKED
        LIMIT 1
        """, nativeQuery = true)
    Optional<UUID> selectOneQueuedIdForUpdate();

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE job
           SET status = 'RUNNING',
               updated_at = now(),
               attempts = COALESCE(attempts,0) + 1
         WHERE id = :id
           AND status = 'QUEUED'
        """, nativeQuery = true)
    int markRunning(@Param("id") UUID id);

    @Modifying @Transactional
    @Query(value = """
        UPDATE job
           SET status = 'COMPLETE',
               updated_at = now(),
               result = CAST(:resultJson AS jsonb)
         WHERE id = :id
        """, nativeQuery = true)
    int markDone(@Param("id") UUID id, @Param("resultJson") String resultJson);

    @Modifying @Transactional
    @Query(value = """
        UPDATE job
           SET status = 'FAILED',
               updated_at = now(),
               result = CAST(:errorJson AS jsonb)
         WHERE id = :id
        """, nativeQuery = true)
    int markError(@Param("id") UUID id, @Param("errorJson") String errorJson);

    // ---- Dedup (optioneel) ----
    @Query("""
       select j from Job j
       where j.dedupKey = :dedup
         and j.status in (com.example.clipbot_backend.util.JobStatus.QUEUED,
                          com.example.clipbot_backend.util.JobStatus.RUNNING)
       order by j.createdAt desc
    """)
    Optional<Job> findQueuedOrRunningByDedupKey(@Param("dedup") String dedupKey);



}
