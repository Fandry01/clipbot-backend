package com.example.clipbot_backend.repository;

import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Page<Project> findByOwnerOrderByCreatedAtDesc(Account owner, Pageable pageable);

    @Query("select p from Project p where p.owner.externalSubject = :ownerExternalSubject and p.normalizedSourceUrl = :normalizedUrl")
    Optional<Project> findByOwnerAndNormalizedUrl(@Param("ownerExternalSubject") String ownerExternalSubject,
                                                 @Param("normalizedUrl") String normalizedUrl);
}
