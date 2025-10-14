package com.example.clipbot_backend.repository;

import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Project;
import com.example.clipbot_backend.model.ProjectMediaId;
import com.example.clipbot_backend.model.ProjectMediaLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectMediaRepository extends JpaRepository<ProjectMediaLink, ProjectMediaId> {
    boolean existsByProjectAndMedia(Project project, Media media);
    List<ProjectMediaLink> findByProject(Project project);

    @Query("select pm.media from ProjectMediaLink pm where pm.project = :project")
    List<Media> findMediaByProject(@Param("project") Project project);
}
