package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.*;
import com.example.clipbot_backend.engine.Interfaces.ClipRenderEngine;
import com.example.clipbot_backend.engine.Interfaces.DetectionEngine;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.model.*;
import com.example.clipbot_backend.repository.*;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.example.clipbot_backend.service.Interfaces.SubtitleService;
import com.example.clipbot_backend.util.AssetKind;
import com.example.clipbot_backend.util.ClipStatus;
import com.example.clipbot_backend.util.JobType;
import com.example.clipbot_backend.util.MediaStatus;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class WorkerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerService.class);

    private final JobService jobService;
    private final TranscriptService transcriptService;
    private final MediaRepository mediaRepo;
    private final TranscriptRepository transcriptRepo;
    private final SegmentRepository segmentRepo;
    private final ClipRepository clipRepo;
    private final AssetRepository assetRepo;
    private final UrlDownloader urlDownloader;
    private final FasterWhisperClient fastWhisperClient;
    private final AudioWindowService audioWindowService;

    // engines
     TranscriptionEngine gptDiarizeEngine;
     TranscriptionEngine fasterWhisperEngine;
    private final DetectionEngine detection;
    private final ClipRenderEngine renderEngine;
    private final StorageService storage;
    private final SubtitleService subtitles;


    public WorkerService(JobService jobService, TranscriptService transcriptService, MediaRepository mediaRepo, TranscriptRepository transcriptRepo, SegmentRepository segmentRepo, ClipRepository clipRepo, AssetRepository assetRepo, UrlDownloader urlDownloader, FasterWhisperClient fastWhisperClient, AudioWindowService audioWindowService, DetectionEngine detection, ClipRenderEngine renderEngine, StorageService storage, SubtitleService subtitles, @Qualifier("gptDiarizeEngine")TranscriptionEngine gptDiarizeEngine, @Qualifier("fasterWhisperEngine")TranscriptionEngine fasterWhisperEngine) {
        this.jobService = jobService;
        this.transcriptService = transcriptService;
        this.mediaRepo = mediaRepo;
        this.transcriptRepo = transcriptRepo;
        this.segmentRepo = segmentRepo;
        this.clipRepo = clipRepo;
        this.assetRepo = assetRepo;
        this.urlDownloader = urlDownloader;
        this.fastWhisperClient = fastWhisperClient;
        this.audioWindowService = audioWindowService;
        this.detection = detection;
        this.renderEngine = renderEngine;
        this.storage = storage;
        this.subtitles = subtitles;
        this.gptDiarizeEngine = gptDiarizeEngine;
        this.fasterWhisperEngine = fasterWhisperEngine;
    }

    @Scheduled(fixedDelayString = "3000")
    public void poll() {
        jobService.pickOneQueued().ifPresent(job -> {
            try {
                switch (job.getType()){
                    case TRANSCRIBE -> handleTranscribe(job);
                    case DETECT     -> handleDetect(job);
                    case CLIP       -> handleClip(job);
                    default         -> LOGGER.warn("Unhandeld job type {}", job.getType());
                }
            } catch (Exception e) {
                LOGGER.error("Job {}  Failed: {}", job.getId(), e.getMessage(), e);
                jobService.markError(job.getId(), e.getMessage(), Map.of());
            }
        });
    }

@Transactional
void handleTranscribe(Job job) {
    Objects.requireNonNull(job, "job");
    final UUID mediaId = job.getMedia() != null ? job.getMedia().getId() : null;
    if (mediaId == null) {
        jobService.markError(job.getId(), "MEDIA_MISSING", Map.of());
        return;
    }

    try {
        Media media = mediaRepo.findById(mediaId).orElseThrow();

        // 0) Idempotent: als transcript al bestaat → overslaan en direct DETECT enqueuen
        if (transcriptService.existsAnyFor(mediaId)) {
            jobService.markDone(job.getId(), Map.of("skipped", "already_transcribed"));
            jobService.enqueue(mediaId, JobType.DETECT, Map.of());
            return;
        }

        // 1) ObjectKey normaliseren (fallback voor oude records)
        String key = media.getObjectKey();
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("media.objectKey invalid: " + key);
        }

        boolean isMulti = media.isMultiSpeakerEffective();
        TranscriptionEngine engine = isMulti ? gptDiarizeEngine : fasterWhisperEngine;

        // 2) Inputbestand garanderen in RAW
        Path input;
        String src = media.getSource() == null ? "" : media.getSource().toLowerCase(Locale.ROOT);
        if ("url".equals(src)) {
            // Download naar RAW als het er niet staat. Laat de downloader exact onder `key` plaatsen.
            input = urlDownloader.ensureRawObject(
                    Objects.requireNonNull(media.getExternalUrl(), "externalUrl is null for URL source"),
                    key
            );
        }
        media.setStatus(MediaStatus.PROCESSING);
        mediaRepo.save(media);

        long t0 = System.nanoTime();

        // 3) Transcribe
        var req = new TranscriptionEngine.Request(
                media.getId(),
                key,          // laat engine zelf resolveRaw(key) doen (liefst via StorageService-injectie)
                null          // extra opties (bijv. target lang) optioneel
        );
        var res = engine.transcribe(req);

        // 4) Transcript upsert
        transcriptService.upsert(media.getId(), res);


        // 5) Job afronden + Detect enqueuen
        jobService.markDone(job.getId(), Map.of(
                "lang", res.lang(),
                "provider", res.provider(),
                "durationMs", (System.nanoTime() - t0) / 1_000_000
        ));
        jobService.enqueue(media.getId(), JobType.DETECT, Map.of(
                "lang", res.lang(),
                "provider", res.provider()
        ));

        LOGGER.info("TRANSCRIBE {} OK in {} ms (lang={}, provider={})",
                mediaId, (System.nanoTime() - t0) / 1_000_000, res.lang(), res.provider());

    } catch (Exception ex) {
        LOGGER.error("TRANSCRIBE {} failed: {}", mediaId, ex.toString(), ex);
        jobService.markError(job.getId(), ex.getMessage(), Map.of("stack", stackTop(ex)));
    }
}
    private String stackTop(Throwable ex) {
        var sw = new java.io.StringWriter();
        ex.printStackTrace(new java.io.PrintWriter(sw));
        // om payload compact te houden kun je eventueel alleen de eerste ~1–2 KB bewaren
        var s = sw.toString();
        return s.length() > 2000 ? s.substring(0, 2000) + "…(truncated)" : s;
    }

    @Transactional
    void handleDetect(Job job) throws Exception {
        var media = mediaRepo.findById(job.getMedia().getId()).orElseThrow();
        //1) Transcript kiezen payload > latest by createdAt
        Optional<Transcript> trOpt = Optional.empty();

        String lang = (String) job.getPayload().getOrDefault("lang", null);
        String provider = (String) job.getPayload().getOrDefault("provider", null);

        if(lang != null && provider != null) {
            trOpt = transcriptRepo.findByMediaAndLangAndProvider(media, lang, provider);
        } else {
            trOpt = transcriptRepo.findTopByMediaOrderByCreatedAtDesc(media);
        }
        if (trOpt.isEmpty()) {
            LOGGER.warn("No transcript for media {}. Enqueue TRANSCRIBE and skip DETECT.", media.getId());
            jobService.markError(job.getId(), "No transcript", Map.of());
            jobService.enqueue(media.getId(), JobType.TRANSCRIBE, Map.of());
            return;
        }
        var tr = trOpt.get();

        var srcPath = storage.resolveRaw(media.getObjectKey());
        var params = DetectionParams.defaults().withMaxCandidates(8);
        Object sceneThresholdValue = job.getPayload().get("sceneThreshold");
        Double sceneTh = null;
        if (sceneThresholdValue instanceof Number number) {
            sceneTh = number.doubleValue();
        } else if (sceneThresholdValue != null) {
            try {
                sceneTh = Double.parseDouble(sceneThresholdValue.toString());
            } catch (NumberFormatException ignored) {
                LOGGER.warn("Invalid sceneThreshold payload value: {}", sceneThresholdValue);
            }
        }
        if (sceneTh != null && sceneTh > 0) {
            params = new DetectionParams(
                    params.minDurationMs(), params.maxDurationMs(), params.maxCandidates(),
                    params.silenceNoiseDb(), params.silenceMinDurSec(), params.snapThresholdMs(),
                    params.targetLenSec(), params.lenSigmaSec(), sceneTh, params.snapSceneMs(), params.sceneAlignBonus()
            );
        }
        long T0 = System.nanoTime();
        var detected = detection.detect(srcPath, tr, params);

        long pad = 500;
        List<SegmentDTO> refined = new ArrayList<>(detected.size());
        for (var s : detected) {
            var win = audioWindowService.sliceToTempWav(srcPath, s.startMs(), s.endMs(), pad);
            var fw = fastWhisperClient.transcribeFile(win.file(), true);
            var snapped = snapToWordBounds(s, fw, win.offsetMs());
            refined.add(snapped);
            try { Files.deleteIfExists(win.file()); } catch (Exception ignore) {}
        }

        // 4) Idempotent wegschrijven
        segmentRepo.deleteByMedia(media);
        if(!refined.isEmpty()) {
            var toSave = new ArrayList<Segment>(refined.size());
            // save batch
            for(var s : refined) {
                var seg = new Segment(media, s.startMs(), s.endMs());
                seg.setScore(s.score());
                seg.setMeta(s.meta());
                toSave.add(seg);
            }
            segmentRepo.saveAll(toSave);
        }


        jobService.markDone(job.getId(), Map.of("segmentCount", refined.size()));
    }

    @Transactional
      void handleClip(Job job) throws Exception {
             //verwacht payload: clipId
            var clipId = UUID.fromString(String.valueOf(job.getPayload().get("clipId")));
            var clip = clipRepo.findById(clipId).orElseThrow();
            var media = clip.getMedia();
            var srcPath = storage.resolveRaw(media.getObjectKey());

            // subtitles (optioneel)

            var tr = transcriptRepo.findByMediaAndLangAndProvider(media, "en", "openai").orElse(null);
            SubtitleFiles subs = null;
            if (tr != null) {
                subs = subtitles.buildSubtitles(tr, clip.getStartMs(), clip.getEndMs());
            }

            RenderOptions options = RenderOptions.withDefaults(clip.getMeta(), subs);
            var res = renderEngine.render(srcPath, clip.getStartMs(), clip.getEndMs(), options);

            // Registreer Assets
            var owner = media.getOwner();
            assetRepo.save(new Asset(owner, AssetKind.CLIP_MP4, res.mp4Key(), res.mp4Size()));
            if(res.thumbKey() != null) assetRepo.save(new Asset(owner, AssetKind.THUMBNAIL, res.thumbKey(), res.thumbSize()));

            if(subs != null){
                if(subs.srtKey() != null) assetRepo.save(new Asset(owner, AssetKind.SUB_SRT,subs.srtKey(), subs.srtSize()));
                if(subs.vttKey() != null) assetRepo.save(new Asset(owner, AssetKind.SUB_VTT, subs.vttKey(), subs.vttSize()));
            }

            // update clip
            clip.setCaptionSrtKey(subs != null ? subs.srtKey() : null);
            clip.setCaptionVttKey(subs != null ? subs.vttKey() : null);
            clip.setStatus(ClipStatus.READY);
            clipRepo.save(clip);

            jobService.markDone(job.getId(), Map.of("mp4Key", res.mp4Key()));
            LOGGER.info("CLIP {} ready (mp4Key={})", clipId, res.mp4Key());

    }
    private SegmentDTO snapToWordBounds(SegmentDTO seg, FwVerboseResponse fw, long offsetMs) {
        long s = seg.startMs();
        long e = seg.endMs();
        long bestS = s, bestE = e;

        if (fw != null && fw.segments() != null) {
            for (var fs : fw.segments()) {
                if (fs.words() == null) continue;
                for (var w : fs.words()) {
                    long ws = Math.round((w.start() == null ? 0.0 : w.start()) * 1000) + offsetMs;
                    long we = Math.round((w.end()   == null ? 0.0 : w.end())   * 1000) + offsetMs;

                    if (Math.abs(ws - s) < Math.abs(bestS - s)) bestS = ws;
                    if (Math.abs(we - e) < Math.abs(bestE - e)) bestE = we;
                }
            }
        }
        if (bestE <= bestS) { bestS = s; bestE = e; }

        Map<String,Object> meta = new LinkedHashMap<>(seg.meta()==null?Map.of():seg.meta());
        meta.put("refined", true);
        meta.put("offsetMs", offsetMs);
        return new SegmentDTO(bestS, bestE, seg.score(), meta);
    }


}
