package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.SegmentDTO;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Segment;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.SegmentRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SegmentService {
private final SegmentRepository segmentRepo;
private final MediaRepository mediaRepo;

    public SegmentService(SegmentRepository segmentRepo, MediaRepository mediaRepo) {
        this.segmentRepo = segmentRepo;
        this.mediaRepo = mediaRepo;
    }

    @Transactional
    public void saveBatch(UUID mediaId, List<SegmentDTO> items) {
        Media media = mediaRepo.findById(mediaId).orElseThrow();
        for(SegmentDTO segment : items) {
            Segment seg = new Segment(media, segment.startMs(), segment.endMs());
            seg.setScore(segment.score());
            seg.setMeta(segment.meta());
            segmentRepo.save(seg);
        }
    }

    public Page<Segment> list(UUID mediaId, Pageable pageable) {
        Media media = mediaRepo.findById(mediaId).orElseThrow();
        return segmentRepo.findByMedia(media, pageable);
    }

    public List<Segment> topByScore(UUID mediaId, int limit){
        Media media = mediaRepo.findById(mediaId).orElseThrow();
        return segmentRepo.findTopByMediaOrderByScoreDesc(media, PageRequest.of(0, limit));
    }
    @Transactional
    public void deleteByMedia(UUID mediaId) {
        Media media = mediaRepo.findById(mediaId).orElseThrow();
        segmentRepo.deleteAll(segmentRepo.findByMedia(media, org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE)).getContent());
    }
}
