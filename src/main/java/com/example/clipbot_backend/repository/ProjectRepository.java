package com.example.clipbot_backend.repository;

import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Page<Project> findByOwnerOrderByCreatedAtDesc(Account owner, Pageable pageable);
}
