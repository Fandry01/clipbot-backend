package com.example.clipbot_backend.repository;

import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Transcript;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TranscriptRepository extends JpaRepository<Transcript, UUID> {
    Optional<Transcript> findByMediaAndLangAndProvider(Media media, String lang, String provider);
    List<Transcript> findAllByMedia(Media media);
    Optional<Transcript> findTopByMediaOrderByCreatedAtDesc(Media media);
    boolean existsByMedia(Media media);

}
