package com.example.clipbot_backend.service;


import com.example.clipbot_backend.model.Clip;
import com.example.clipbot_backend.model.Job;
import com.example.clipbot_backend.repository.ClipRepository;
import com.example.clipbot_backend.repository.JobRepository;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.SegmentRepository;
import com.example.clipbot_backend.util.ClipStatus;
import com.example.clipbot_backend.util.JobStatus;
import com.example.clipbot_backend.util.JobType;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClipService {
    private final ClipRepository clipRepo;
    private final MediaRepository mediaRepo;
    private final SegmentRepository segmentRepo;
    private final JobRepository jobRepo;

    public ClipService(ClipRepository clipRepo, MediaRepository mediaRepo, SegmentRepository segmentRepo, JobRepository jobRepo) {
        this.clipRepo = clipRepo;
        this.mediaRepo = mediaRepo;
        this.segmentRepo = segmentRepo;
        this.jobRepo = jobRepo;
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
    public UUID enqueueRender(JobService jobs, UUID clipId) {
        var clip = get(clipId);
        String dedup = "clip:" + clipId;

        // status naar QUEUED als hij nog niet in de renderflow zit
        if (clip.getStatus() != ClipStatus.QUEUED && clip.getStatus() != ClipStatus.RENDERING) {
            clip.setStatus(ClipStatus.QUEUED);
            clipRepo.saveAndFlush(clip);
        }

        Map<String,Object> payload = Map.of("clipId", clipId.toString());
        // mediaId is handig voor correlatie, neem die mee
        UUID mediaId = clip.getMedia() != null ? clip.getMedia().getId() : null;

        return jobs.enqueueUnique(mediaId, JobType.CLIP, dedup, payload);
    }



    //save method
    public Clip save(Clip clip){
        return clipRepo.save(clip);

    }


}
