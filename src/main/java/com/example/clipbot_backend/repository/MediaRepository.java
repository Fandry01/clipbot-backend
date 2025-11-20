package com.example.clipbot_backend.repository;

import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.util.MediaStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MediaRepository extends JpaRepository<Media, UUID> {
    Page<Media> findByOwnerOrderByCreatedAtDesc(Account owner, Pageable pageable);
    Page<Media> findByOwnerAndStatusOrderByCreatedAtDesc(Account owner, MediaStatus status, Pageable pageable);
    @Query("""
       select m
       from Media m
       join fetch m.owner
       where m.id = :id
       """)
    Optional<Media> findByIdWithOwner(@Param("id") UUID id);

    @Query("""
           select m from Media m
           join fetch m.owner o
           where m.id = :id and o.externalSubject = :subj
           """)
    Optional<Media> findOwned(@Param("id") UUID id, @Param("subj") String subj);
}
