package com.example.clipbot_backend.repository;

import com.example.clipbot_backend.model.PlanLimits;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for plan limit configuration records.
 */
public interface PlanLimitsRepository extends JpaRepository<PlanLimits, String> {
}
