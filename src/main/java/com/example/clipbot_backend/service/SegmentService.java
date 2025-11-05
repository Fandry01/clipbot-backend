package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.SegmentDTO;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Segment;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.SegmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        var media = mediaRepo.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));
        var toSave = new ArrayList<Segment>(items.size());
        for (var s : items) {
            if (s.startMs() < 0 || s.endMs() <= s.startMs()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_SEGMENT_BOUNDS");
            }
            var e = new Segment(media, s.startMs(), s.endMs());
            e.setScore(s.score() != null ? s.score() : BigDecimal.ZERO);
            e.setMeta(s.meta() != null ? s.meta() : Map.of());
            toSave.add(e);
        }
        segmentRepo.saveAll(toSave);
    }

    @Transactional(readOnly = true)
    public Page<Segment> list(UUID mediaId, Pageable pageable) {
        var media = mediaRepo.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));
        return segmentRepo.findByMediaOrderByStartMsAsc(media, pageable);
    }

    @Transactional(readOnly = true)
    public List<Segment> topByScore(UUID mediaId, int limit) {
        var media = mediaRepo.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));
        int n = Math.max(0, Math.min(limit, 1000));
        return segmentRepo.findTopByMediaOrderByScoreDesc(media, PageRequest.of(0, n));
    }



    @Transactional
    public void deleteByMedia(UUID mediaId) {
        var media = mediaRepo.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));
        segmentRepo.deleteByMedia(media);
    }
}
