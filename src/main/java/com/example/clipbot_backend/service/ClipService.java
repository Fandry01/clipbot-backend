package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.ClipResponse;
import com.example.clipbot_backend.model.Clip;
import com.example.clipbot_backend.model.Segment;
import com.example.clipbot_backend.repository.ClipRepository;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.SegmentRepository;
import com.example.clipbot_backend.util.ClipStatus;
import com.example.clipbot_backend.util.JobType;
import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
        var media = mediaRepo.findById(mediaId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"MEDIA_NOT_FOUND"));
        var seg = segmentRepo.findById(segmentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SEGMENT_NOT_FOUND"));
        if(!seg.getMedia().getId().equals(mediaId)){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SEGMENT_NOT_IN_MEDIA");
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
        var media = mediaRepo.findById(mediaId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));
        if(startMs < 0 || endMs <= startMs) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BOUNDS");
        if(media.getDurationMs() != null && media.getDurationMs() > 0 && endMs > media.getDurationMs())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "END_BEYOND_DURATION");
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

    @Deprecated
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
        return clipRepo.findById(clipId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Clip not found: " + clipId));
    }
    @Transactional
    public @Nullable UUID enqueueRender(JobService jobService, UUID clipId) {
        var clip = get(clipId);

        // Als hij al bezig is, niks doen (idempotent gedrag)
        if (clip.getStatus() == ClipStatus.RENDERING || clip.getStatus() == ClipStatus.QUEUED) {
            // eventueel: return bestaand jobId als je dat bijhoudt
            return null;
        }
        clip.setStatus(ClipStatus.QUEUED);
        clipRepo.saveAndFlush(clip);
        return jobService.enqueue(clip.getMedia().getId(), JobType.CLIP, Map.of("clipId", clipId.toString()));
    }

    //save method
    public Clip save(Clip clip){
        return clipRepo.save(clip);

    }


}
