package com.example.clipbot_backend.service;


import com.example.clipbot_backend.dto.RenderSpec;
import com.example.clipbot_backend.model.Clip;

import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.repository.AssetRepository;
import com.example.clipbot_backend.repository.ClipRepository;
import com.example.clipbot_backend.repository.JobRepository;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.SegmentRepository;

import com.example.clipbot_backend.util.ClipStatus;

import com.example.clipbot_backend.util.JobType;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ClipService {
    private static final Logger log = LoggerFactory.getLogger(ClipService.class);

    private final ClipRepository clipRepo;
    private final MediaRepository mediaRepo;
    private final SegmentRepository segmentRepo;
    private final JobRepository jobRepo;
    private final EntitlementService entitlementService;
    private final RenderProfileResolver renderProfileResolver;
    private final AssetRepository assetRepository;

    public ClipService(ClipRepository clipRepo,
                       MediaRepository mediaRepo,
                       SegmentRepository segmentRepo,
                       JobRepository jobRepo,
                       EntitlementService entitlementService,
                       RenderProfileResolver renderProfileResolver,
                       AssetRepository assetRepository) {
        this.clipRepo = clipRepo;
        this.mediaRepo = mediaRepo;
        this.segmentRepo = segmentRepo;
        this.jobRepo = jobRepo;
        this.entitlementService = entitlementService;
        this.renderProfileResolver = renderProfileResolver;
        this.assetRepository = assetRepository;
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

    /**
     * Haalt een clip op inclusief media en owner zodat ownership-checks buiten een transactie kunnen gebeuren.
     *
     * @param clipId clip-identificatie.
     * @return clip met eagerly geladen media en owner.
     * @throws ResponseStatusException wanneer de clip niet bestaat.
     */
    @Transactional(readOnly = true)
    public Clip getWithMedia(UUID clipId) {
        return clipRepo.findByIdWithMedia(clipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clip not found: " + clipId));
    }
    @Transactional
    public UUID enqueueRender(JobService jobs, UUID clipId) {
        Objects.requireNonNull(jobs, "jobService");
        Objects.requireNonNull(clipId, "clipId");
        Objects.requireNonNull(entitlementService, "entitlementService");
        Objects.requireNonNull(renderProfileResolver, "renderProfileResolver");

        var clip = get(clipId);
        Media media = clip.getMedia();
        var owner = media.getOwner();

        // 1) Vraag entitlement op met de gewenste profielnaam (uit DEFAULT)
        String requestedProfile = RenderSpec.DEFAULT.profile();
        EntitlementService.Decision dec = entitlementService.checkCanRender(owner, requestedProfile);

        if (!dec.allow()) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, dec.reason());
        }

        // 2) Bouw de uiteindelijke RenderSpec op basis van DEFAULT + beslissing
        //    - profiel komt uit entitlement (kan bv. geforceerd 'youtube-720p' zijn)
        //    - watermarkEnabled komt uit entitlement
        RenderSpec base = RenderSpec.DEFAULT;
        RenderSpec resolvedSpec = new RenderSpec(
                base.width(),
                base.height(),
                base.fps(),
                base.crf(),
                base.preset(),
                (dec.forcedProfile() == null || dec.forcedProfile().isBlank()) ? base.profile() : dec.forcedProfile(),
                dec.watermark(),
                base.watermarkPath()
        );

        String dedup = "clip:" + clipId;

        // 3) Status naar QUEUED als nog niet in flow
        if (clip.getStatus() != ClipStatus.QUEUED && clip.getStatus() != ClipStatus.RENDERING) {
            clip.setStatus(ClipStatus.QUEUED);
            clipRepo.saveAndFlush(clip);
        }

        // 4) Payload voor job
        Map<String,Object> payload = new HashMap<>();
        payload.put("clipId", clipId.toString());
        payload.put("profile", resolvedSpec.profile());
        payload.put("watermarkEnabled", resolvedSpec.watermarkEnabled());
        if (Boolean.TRUE.equals(resolvedSpec.watermarkEnabled()) && resolvedSpec.watermarkPath() != null) {
            payload.put("watermarkPath", resolvedSpec.watermarkPath());
        }
        UUID mediaId = media != null ? media.getId() : null;

        // 5) Queue + quota burn
        UUID jobId = jobs.enqueueUnique(mediaId, JobType.CLIP, dedup, payload);
        entitlementService.burnOneRender(owner);

        org.slf4j.LoggerFactory.getLogger(ClipService.class).info(
                "Render enqueued account={} plan={} profile={} watermark={}",
                owner.getId(), owner.getPlanTier(), resolvedSpec.profile(), resolvedSpec.watermarkEnabled()
        );
        return jobId;
    }




    //save method
    public Clip save(Clip clip){
        return clipRepo.save(clip);

    }

    /**
     * Verwijdert een clip en alle gekoppelde assets.
     *
     * @param clip clip-entiteit die verwijderd moet worden, inclusief geladen media/owner.
     */
    @Transactional
    public void deleteClip(Clip clip) {
        Objects.requireNonNull(clip, "clip");

        var media = clip.getMedia();
        var owner = media != null ? media.getOwner() : null;
        log.info("START delete clip id={} mediaId={} ownerId={}",
                clip.getId(),
                media != null ? media.getId() : null,
                owner != null ? owner.getId() : null);

        assetRepository.deleteByRelatedClipIn(List.of(clip));
        clipRepo.delete(clip);

        log.info("DONE delete clip id={} mediaId={} ownerId={}",
                clip.getId(),
                media != null ? media.getId() : null,
                owner != null ? owner.getId() : null);
    }


}
