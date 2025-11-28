package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.DetectionParams;
import com.example.clipbot_backend.dto.SegmentDTO;
import com.example.clipbot_backend.engine.Interfaces.DetectionEngine;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Segment;
import com.example.clipbot_backend.model.Transcript;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.SegmentRepository;
import com.example.clipbot_backend.repository.TranscriptRepository;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.example.clipbot_backend.util.JobType;
import com.example.clipbot_backend.util.MediaStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class DetectionService {
    private final JobService jobService;
    private final MediaRepository mediaRepo;
    private final TranscriptRepository transcriptRepo;
    private final SegmentRepository segmentRepo;
    private final StorageService storageService;
    private final DetectionEngine detectionEngine;

    public DetectionService(JobService jobService, MediaRepository mediaRepo, TranscriptRepository transcriptRepo, SegmentRepository segmentRepo, StorageService storageService, DetectionEngine detectionEngine) {
        this.jobService = jobService;
        this.mediaRepo = mediaRepo;
        this.transcriptRepo = transcriptRepo;
        this.segmentRepo = segmentRepo;
        this.storageService = storageService;
        this.detectionEngine = detectionEngine;
    }

    public UUID enqueueDetect(UUID mediaId, String lang, String provider, Double sceneThreshold){
        return enqueueDetect(mediaId, lang, provider, sceneThreshold, null, null);
    }

    public UUID enqueueDetect(UUID mediaId,
                              String lang,
                              String provider,
                              Double sceneThreshold,
                              Integer topN,
                              Boolean enqueueRender) {
        Media media = mediaRepo.findById(mediaId).orElseThrow();
        Map<String, Object> payload = new LinkedHashMap<>();
        if (lang != null) payload.put("lang", lang);
        if (provider != null) payload.put("provider", provider);
        if (sceneThreshold != null) payload.put("sceneThreshold", sceneThreshold);
        if (topN != null) payload.put("topN", topN);
        if (enqueueRender != null) payload.put("enqueueRender", enqueueRender);
        return jobService.enqueue(media.getId(), JobType.DETECT, payload);
    }

    @Transactional
    public List<SegmentDTO> runDetectNow(UUID mediaId, DetectNowOptions options) throws Exception {
        Media media = mediaRepo.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));
        media.setStatus(MediaStatus.PROCESSING);
        mediaRepo.saveAndFlush(media);

        try {
            Transcript transcript = resolveTranscript(media, options.lang(), options.provider());
            if (transcript == null) return List.of();

            Path srcPath = storageService.resolveRaw(media.getObjectKey());
            if (srcPath == null || !Files.exists(srcPath))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RAW_NOT_FOUND");

            DetectionParams params = buildParams(options);
            var detected = detectionEngine.detect(srcPath, transcript, params);

            segmentRepo.deleteByMedia(media);
            if (!detected.isEmpty()) {
                List<Segment> entities = new ArrayList<>(detected.size());
                for (var s : detected) {
                    Segment e = new Segment(media, s.startMs(), s.endMs());
                    e.setScore(Optional.ofNullable(s.score()).orElse(BigDecimal.ZERO));
                    e.setMeta(s.meta() == null ? Map.of() : s.meta());
                    entities.add(e);
                }
                segmentRepo.saveAll(entities);
            }

            media.setStatus(MediaStatus.READY);
            mediaRepo.save(media);

            return detected;
        } catch (Exception e) {
            media.setStatus(MediaStatus.FAILED);
            mediaRepo.save(media);
            throw e;
        }
    }

    public List<PersistedSegmentView> listSegments(UUID mediaId,int page, int size) {
        Media media = mediaRepo.findById(mediaId).orElseThrow();
        var pageable = PageRequest.of(page, size);
        return segmentRepo.findByMediaOrderByStartMsAsc(media, pageable).stream()
                .map(s -> new PersistedSegmentView(s.getStartMs(), s.getEndMs(),
                        s.getScore() != null ? s.getScore() : BigDecimal.ZERO,
                        s.getMeta() != null ? s.getMeta() : Map.of()))
                .toList();
    }

    private Transcript resolveTranscript(Media media, String lang, String provider) {
        if (lang != null && provider != null) {
            return transcriptRepo.findByMediaAndLangAndProvider(media,lang,"openai").orElse(null);
        }
        return transcriptRepo.findTopByMediaOrderByCreatedAtDesc(media).orElse(null);
    }

    private DetectionParams buildParams(DetectNowOptions opts) {
        var p = DetectionParams.defaults();
        if (opts == null) return p;
        if (opts.maxCandidates() != null) p = p.withMaxCandidates(opts.maxCandidates());
        if (opts.sceneThreshold() != null) {
            p = new DetectionParams(
                    p.minDurationMs(), p.maxDurationMs(), p.maxCandidates(),
                    p.silenceNoiseDb(), p.silenceMinDurSec(), p.snapThresholdMs(),
                    p.targetLenSec(), p.lenSigmaSec(),
                    opts.sceneThreshold(), p.snapSceneMs(), p.sceneAlignBonus()
            );
        }
        return p;
    }
    public record DetectNowOptions(String lang, String provider, Integer maxCandidates, Double sceneThreshold) {}
    public record PersistedSegmentView(long startMs, long endMs, BigDecimal score, Map<String,Object> meta) {}
}
