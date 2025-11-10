package com.example.clipbot_backend.repository;

import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Project;
import com.example.clipbot_backend.model.ProjectMediaId;
import com.example.clipbot_backend.model.ProjectMediaLink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ProjectMediaRepository extends JpaRepository<ProjectMediaLink, ProjectMediaId> {
    boolean existsByProjectAndMedia(Project project, Media media);
    List<ProjectMediaLink> findByProject(Project project);

    @Query("""
           select pml.media
           from ProjectMediaLink pml
           where pml.project = :project
           order by pml.createdAt desc
           """)
    List<Media> findMediaByProject(@Param("project") Project project);

    Optional<ProjectMediaLink> findByProjectAndMedia(Project project, Media media);
    @Modifying
    @Transactional
    void deleteByProjectAndMedia(Project project, Media media);
    @Query("select pml from ProjectMediaLink pml where pml.project = :project order by pml.createdAt desc")
    List<ProjectMediaLink> findByProjectOrderByCreatedAtDesc(@Param("project") Project project);

    Page<ProjectMediaLink> findByProject(Project project, Pageable pageable);

    @Query("""
       select pml.media
       from ProjectMediaLink pml
       where pml.project = :project
       order by pml.createdAt desc
       """)
    Page<Media> findMediaByProject(@Param("project") Project project, Pageable pageable);

    @Query("select pml from ProjectMediaLink pml where pml.project = :project order by pml.createdAt desc")
    Page<ProjectMediaLink> findByProjectOrderByCreatedAtDesc(@Param("project") Project project, Pageable pageable);

}
