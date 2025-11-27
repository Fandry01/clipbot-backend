package com.example.clipbot_backend.repository;

import com.example.clipbot_backend.model.OneClickOrchestration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OneClickOrchestrationRepository extends JpaRepository<OneClickOrchestration, UUID> {
    Optional<OneClickOrchestration> findByOwnerExternalSubjectAndIdempotencyKey(String ownerExternalSubject, String idempotencyKey);
}
