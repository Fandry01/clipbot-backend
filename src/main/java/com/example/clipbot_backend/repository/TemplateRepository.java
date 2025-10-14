package com.example.clipbot_backend.repository;

import com.example.clipbot_backend.model.Template;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TemplateRepository extends JpaRepository<Template, UUID> {
    Page<Template> findAllByOwnerId(UUID ownerId, Pageable pageable);
    Optional<Template> findByIdAndOwnerId(UUID id, UUID ownerId);
}
