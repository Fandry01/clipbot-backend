package com.example.clipbot_backend.repository;

import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Segment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SegmentRepository extends JpaRepository<Segment, UUID> {
    @Query("select s from Segment s where s.media = :media order by s.score desc nulls last, s.createdAt desc")
    List<Segment> findTopByMediaOrderByScoreDesc(Media media, Pageable pageable);

    Page<Segment> findByMedia(Media media, Pageable pageable);
}
