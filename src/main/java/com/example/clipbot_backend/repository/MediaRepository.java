package com.example.clipbot_backend.repository;

import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.util.MediaStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MediaRepository extends JpaRepository<Media, UUID> {
    Page<Media> findByOwnerOrderByCreatedAtDesc(Account owner, Pageable pageable);
    Page<Media> findByOwnerAndStatusOrderByCreatedAtDesc(Account owner, MediaStatus status, Pageable pageable);
}
