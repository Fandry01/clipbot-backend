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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
        Media media = mediaRepo.findById(mediaId).orElseThrow();
        Map<String,Object> payload = new LinkedHashMap<>();
        if (lang != null) payload.put("lang", lang);
        if (provider != null) payload.put("provider", provider);
        if (sceneThreshold != null) payload.put("sceneThreshold", sceneThreshold);
        return jobService.enqueue(media.getId(), JobType.DETECT, payload);
    }

    @Transactional
    public List<SegmentDTO> runDetectNow(UUID mediaId, DetectNowOptions options) throws Exception{
        Media media = mediaRepo.findById(mediaId).orElseThrow();
        Transcript transcript = resolveTranscript(media, options.lang(), options.provider());
        if(transcript == null) return List.of();

        DetectionParams params = buildParams(options);
        Path srcPath = storageService.resolveRaw(media.getObjectKey());

        var segments = detectionEngine.detect(srcPath, transcript, params);

        segmentRepo.deleteByMedia(media);
        if(!segments.isEmpty()){
            List<Segment> entities = new ArrayList<>(segments.size());
            for (var s : segments) {
                Segment e = new Segment(media, s.startMs(), s.endMs());
                e.setScore(s.score() != null ? s.score() : BigDecimal.ZERO);
                e.setMeta(s.meta() != null ? s.meta() : Map.of());
                entities.add(e);
            }
            segmentRepo.saveAll(entities);
        }
        return  segments;
    }
    public List<PersistedSegmentView> listSegments(UUID mediaId) {
        Media media = mediaRepo.findById(mediaId).orElseThrow();
        return segmentRepo.findByMediaOrderByStartMsAsc(media).stream()
                .map(s -> new PersistedSegmentView(s.getStartMs(), s.getEndMs(),
                        s.getScore() != null ? s.getScore() : BigDecimal.ZERO,
                        s.getMeta() != null ? s.getMeta() : Map.of()))
                .toList();
    }

    private Transcript resolveTranscript(Media media, String lang, String provider) {
        if (lang != null && provider != null) {
            return transcriptRepo.findByMediaAndLangAndProvider(media).orElse(null);
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
