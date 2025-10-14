package com.example.clipbot_backend.repository;

import com.example.clipbot_backend.model.Clip;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.util.ClipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface ClipRepository extends JpaRepository<Clip, UUID> {
    Page<Clip> findByMediaOrderByCreatedAtDesc(Media media, Pageable pageable);
    Page<Clip> findByMediaAndStatusOrderByCreatedAtDesc(Media media, ClipStatus status, Pageable pageable);
    Page<Clip> findByMediaInOrderByCreatedAtDesc(Collection<Media> media, Pageable pageable);
    Page<Clip> findByMediaInAndStatusOrderByCreatedAtDesc(Collection<Media> media, ClipStatus status, Pageable pageable);
}
