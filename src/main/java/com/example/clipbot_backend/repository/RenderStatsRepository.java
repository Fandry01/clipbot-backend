package com.example.clipbot_backend.repository;

import com.example.clipbot_backend.model.RenderStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for tracking render statistics per profile kind.
 */
public interface RenderStatsRepository extends JpaRepository<RenderStats, UUID> {
    /**
     * Finds statistics for the given kind.
     *
     * @param kind render kind identifier.
     * @return optional statistics entity.
     */
    Optional<RenderStats> findByKind(String kind);
}
