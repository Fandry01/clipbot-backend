package com.example.clipbot_backend.service;

import com.example.clipbot_backend.model.Clip;
import com.example.clipbot_backend.model.Segment;
import com.example.clipbot_backend.repository.ClipRepository;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.SegmentRepository;
import com.example.clipbot_backend.util.ClipStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class ClipService {
    private final ClipRepository clipRepo;
    private final MediaRepository mediaRepo;
    private final SegmentRepository segmentRepo;

    public ClipService(ClipRepository clipRepo, MediaRepository mediaRepo, SegmentRepository segmentRepo) {
        this.clipRepo = clipRepo;
        this.mediaRepo = mediaRepo;
        this.segmentRepo = segmentRepo;
    }

    @Transactional
    public UUID createFromSegment(UUID mediaId, UUID segmentId, String title, Map<String, Object> meta) {
        var media = mediaRepo.findById(mediaId).orElseThrow();
        var seg = segmentRepo.findById(segmentId).orElseThrow();
        if(!seg.getMedia().getId().equals(mediaId)){
            throw new IllegalArgumentException("Segment does not belong to media");
        }
        var clip = new Clip(media, seg.getStartMs(), seg.getEndMs());
        clip.setSourceSegment(seg);
        clip.setTitle(title);
        clip.setMeta(meta);
        clip.setStatus(ClipStatus.QUEUED);
        clipRepo.save(clip);
        return clip.getId();
    }

    @Transactional
    public UUID createCustom(UUID mediaId, long startMs, long endMs,String title,  Map<String, Object> meta) {
        var media = mediaRepo.findById(mediaId).orElseThrow();
        if(startMs < 0 || endMs <= startMs) throw new IllegalArgumentException("Invalid bounds");
        if(media.getDurationMs() > 0 && endMs > media.getDurationMs()) throw new IllegalArgumentException("Clip end beyond media duration");
        var clip = new Clip(media, startMs, endMs);
        clip.setTitle(title);
        clip.setMeta(meta);
        clip.setStatus(ClipStatus.QUEUED);
        clipRepo.save(clip);
        return clip.getId();
    }

    @Transactional
    public void setStatus(UUID clipId, ClipStatus status) {
        var clip = clipRepo.findById(clipId).orElseThrow();
        clip.setStatus(status);
        clipRepo.save(clip);
    }

    @Transactional
    public void setCaptions(UUID clipId, String srtKey, String vttKey){
        var clip = clipRepo.findById(clipId).orElseThrow();
        clip.setCaptionSrtKey(srtKey);
        clip.setCaptionVttKey(vttKey);
    }

    public Page<Clip> listByMedia(UUID mediaId, Pageable pageable) {
        var media = mediaRepo.findById(mediaId).orElseThrow();
        return clipRepo.findByMediaOrderByCreatedAtDesc(media, pageable);
    }
    public Clip get(UUID clipId) {
        return clipRepo.findById(clipId).orElseThrow();
    }


}
