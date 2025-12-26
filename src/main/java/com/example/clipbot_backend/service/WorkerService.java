package com.example.clipbot_backend.service;

import com.example.clipbot_backend.config.WorkerExecutorProperties;
import com.example.clipbot_backend.dto.*;
import com.example.clipbot_backend.engine.Interfaces.ClipRenderEngine;
import com.example.clipbot_backend.engine.Interfaces.DetectionEngine;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.model.*;
import com.example.clipbot_backend.repository.*;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.example.clipbot_backend.service.Interfaces.SubtitleService;
import com.example.clipbot_backend.service.thumbnail.ThumbnailService;
import com.example.clipbot_backend.service.IngestCleanupService;
import com.example.clipbot_backend.util.AssetKind;
import com.example.clipbot_backend.util.ClipStatus;
import com.example.clipbot_backend.util.JobType;
import com.example.clipbot_backend.util.MediaStatus;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import static com.example.clipbot_backend.util.JobType.DETECT;

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
    private final ProjectMediaRepository projectMediaRepository;
    private final UrlDownloader urlDownloader;
    private final FasterWhisperClient fastWhisperClient;
    private final AudioWindowService audioWindowService;
    private final DetectWorkflow detectWorkflow;
    private final ClipWorkFlow clipWorkFlow;
    private final ClipService clipService;
    private final ThumbnailService thumbnailService;
    private final IngestCleanupService ingestCleanupService;

    private final Executor workerExecutor;
    private final WorkerExecutorProperties workerProperties;
    private final Semaphore clipSemaphore;
    private final Semaphore transcribeSemaphore;
    private final Semaphore detectSemaphore;

    // engines
     TranscriptionEngine gptDiarizeEngine;
     TranscriptionEngine fasterWhisperEngine;
    private final DetectionEngine detection;
    private final ClipRenderEngine renderEngine;
    private final StorageService storage;
    private final SubtitleService subtitles;
    private final RenderService renderService;

    public WorkerService(JobService jobService, TranscriptService transcriptService, MediaRepository mediaRepo, TranscriptRepository transcriptRepo, SegmentRepository segmentRepo, ClipRepository clipRepo, AssetRepository assetRepo, ProjectMediaRepository projectMediaRepository, UrlDownloader urlDownloader, FasterWhisperClient fastWhisperClient, AudioWindowService audioWindowService, DetectWorkflow detectWorkflow, ClipWorkFlow clipWorkFlow, ClipService clipService, ThumbnailService thumbnailService, IngestCleanupService ingestCleanupService, DetectionEngine detection, ClipRenderEngine renderEngine, StorageService storage, SubtitleService subtitles, RenderService renderService, @Qualifier("gptDiarizeEngine")TranscriptionEngine gptDiarizeEngine, @Qualifier("fasterWhisperEngine")TranscriptionEngine fasterWhisperEngine, @Qualifier("workerTaskExecutor") Executor workerExecutor, WorkerExecutorProperties workerProperties) {
        this.jobService = jobService;
        this.transcriptService = transcriptService;
        this.mediaRepo = mediaRepo;
        this.transcriptRepo = transcriptRepo;
        this.segmentRepo = segmentRepo;
        this.clipRepo = clipRepo;
        this.assetRepo = assetRepo;
        this.projectMediaRepository = projectMediaRepository;
        this.urlDownloader = urlDownloader;
        this.fastWhisperClient = fastWhisperClient;
        this.audioWindowService = audioWindowService;
        this.detectWorkflow = detectWorkflow;
        this.clipWorkFlow = clipWorkFlow;
        this.clipService = clipService;
        this.thumbnailService = thumbnailService;
        this.ingestCleanupService = ingestCleanupService;
        this.detection = detection;
        this.renderEngine = renderEngine;
        this.storage = storage;
        this.subtitles = subtitles;
        this.renderService = renderService;
        this.gptDiarizeEngine = gptDiarizeEngine;
        this.fasterWhisperEngine = fasterWhisperEngine;
        this.workerExecutor = workerExecutor;
        this.workerProperties = workerProperties;
        this.clipSemaphore = new Semaphore(Math.max(1, workerProperties.getClip().getMaxConcurrency()));
        this.transcribeSemaphore = new Semaphore(Math.max(1, workerProperties.getTranscribe().getMaxConcurrency()));
        this.detectSemaphore = new Semaphore(Math.max(1, workerProperties.getDetect().getMaxConcurrency()));
    }

    @Scheduled(fixedDelayString = "3000")
    public void poll() {
        List<Job> jobs = jobService.claimQueuedBatch(workerProperties.getPollBatchSize());
        if (jobs.isEmpty()) {
            LOGGER.debug("Worker poll tick – no jobs claimed");
            return;
        }

        LOGGER.info("Worker claimed jobs count={} ids={}", jobs.size(), jobs.stream().map(Job::getId).collect(Collectors.toList()));
        jobs.forEach(this::submitJob);
    }

    private void submitJob(Job job) {
        workerExecutor.execute(() -> runJobWithSemaphore(job));
    }

    private void runJobWithSemaphore(Job job) {
        Semaphore semaphore = semaphoreFor(job.getType());
        boolean acquired = false;
        long t0 = System.nanoTime();
        try {
            if (semaphore != null) {
                semaphore.acquire();
                acquired = true;
            }
            LOGGER.info("JOB START jobId={} type={} media={} project={}", job.getId(), job.getType(), mediaId(job), resolveProjectId(job));
            boolean ok = runJob(job);
            LOGGER.info("JOB {} jobId={} type={} media={} project={} in={}ms", ok ? "DONE" : "FAILED", job.getId(), job.getType(), mediaId(job), resolveProjectId(job), (System.nanoTime() - t0) / 1_000_000);
        } catch (Exception e) {
            LOGGER.error("Job {} failed: {}", job.getId(), e.toString(), e);
            jobService.markError(job.getId(), e.getMessage(), Map.of("stack", stackTop(e)));
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    private boolean runJob(Job job) {
        try {
            return switch (job.getType()) {
                case TRANSCRIBE -> handleTranscribe(job);
                case DETECT -> handleDetect(job);
                case CLIP -> handleClipJob(job);
                case EXPORT -> handleExport(job);
                case RENDER_CLEAN -> handleCleanRender(job);
                default -> {
                    LOGGER.warn("Unhandled job type={} id={}", job.getType(), job.getId());
                    yield false;
                }
            };
        } catch (Exception e) {
            LOGGER.error("Job {} failed: {}", job.getId(), e.toString(), e);
            jobService.markError(job.getId(), e.getMessage(), Map.of("stack", stackTop(e)));
            return false;
        }
    }

    private Semaphore semaphoreFor(JobType type) {
        return switch (type) {
            case CLIP -> clipSemaphore;
            case TRANSCRIBE -> transcribeSemaphore;
            case DETECT -> detectSemaphore;
            default -> null;
        };
    }

    private UUID mediaId(Job job) {
        return job.getMedia() != null ? job.getMedia().getId() : null;
    }

    private UUID resolveProjectId(Job job) {
        if (job.getPayload() == null) {
            return null;
        }
        Object value = job.getPayload().get("projectId");
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

@Transactional
    boolean handleTranscribe(Job job) {
    LOGGER.debug("TRANSCRIBE enter jobId={} mediaId={}", job.getId(), job.getMedia()!=null?job.getMedia().getId():null); // 👈
    Objects.requireNonNull(job, "job");
    final UUID mediaId = job.getMedia() != null ? job.getMedia().getId() : null;
    if (mediaId == null) {
        jobService.markError(job.getId(), "MEDIA_MISSING", Map.of());
        return false;
    }

    Media media = mediaRepo.findById(mediaId).orElseThrow();

    // 0) Idempotent: als transcript al bestaat → overslaan en direct DETECT enqueuen
    if (transcriptService.existsAnyFor(mediaId)) {
        jobService.markDone(job.getId(), Map.of("skipped", "already_transcribed"));
        jobService.enqueue(mediaId, DETECT, forwardedDetectPayload(job, null));
        return true;
    }

    // 1) ObjectKey normaliseren (fallback voor oude records)
    String key = media.getObjectKey();
    if (key == null || key.isBlank()) {
        throw new IllegalArgumentException("media.objectKey invalid: " + key);
    }

    Path rawPath = null;
    boolean rawReady = false;
    try {
        // 2) Inputbestand garanderen in RAW
        String src = media.getSource() == null ? "" : media.getSource().toLowerCase(Locale.ROOT);
        media.setStatus(MediaStatus.PROCESSING);
        mediaRepo.save(media);

        if ("url".equals(src)) {
            rawPath = urlDownloader.ensureRawObject(
                    Objects.requireNonNull(media.getExternalUrl(), "externalUrl is null for URL source"),
                    key
            );
        } else {
            rawPath = storage.resolveRaw(key);
            if (!Files.exists(rawPath)) {
                throw new IllegalStateException("RAW missing for objectKey: " + key);
            }
        }
        rawReady = rawPath != null && Files.exists(rawPath);
        ThumbnailService.ThumbnailRequest thumbRequest = buildThumbnailRequest(media.getId());
        Path preferred = preferredThumbnailSource(rawPath);
        tryExtractThumbnail(thumbRequest, preferred);

        long t0 = System.nanoTime();

        // 3) Transcribe
        boolean isMulti = media.isMultiSpeakerEffective();
        TranscriptionEngine engine = isMulti ? gptDiarizeEngine : fasterWhisperEngine;
        String selectedEngine = isMulti ? "GPT_DIARIZE" : "FASTER_WHISPER";
        LOGGER.info("TRANSCRIBE selectEngine={} mediaId={} objectKey={}", selectedEngine, mediaId, key);

        var req = new TranscriptionEngine.Request(
                media.getId(),
                key,          // laat engine zelf resolveRaw(key) doen (liefst via StorageService-injectie)
                null          // extra opties (bijv. target lang) optioneel
        );
        TranscriptionEngine.Result res;
        try {
            res = engine.transcribe(req);
        } catch (Exception primaryEx) {
            if (isMulti) {
                LOGGER.warn("TRANSCRIBE primary GPT diarize failed mediaId={} reason={} – falling back to FasterWhisper", mediaId, primaryEx.toString());
                try {
                    res = fasterWhisperEngine.transcribe(req);
                    selectedEngine = "FASTER_WHISPER";
                } catch (Exception fallbackEx) {
                    primaryEx.addSuppressed(fallbackEx);
                    throw primaryEx;
                }
            } else {
                throw primaryEx;
            }
        }

        // 4) Transcript upsert
        transcriptService.upsert(media.getId(), res);


        // 5) Job afronden + Detect enqueuen
        jobService.markDone(job.getId(), Map.of(
                "lang", res.lang(),
                "provider", res.provider(),
                "durationMs", (System.nanoTime() - t0) / 1_000_000
        ));
        jobService.enqueue(media.getId(), DETECT, forwardedDetectPayload(job, res));

        LOGGER.info("TRANSCRIBE {} OK in {} ms (lang={}, provider={})",
                mediaId, (System.nanoTime() - t0) / 1_000_000, res.lang(), res.provider());
        return true;

    } catch (Exception ex) {
        LOGGER.error("TRANSCRIBE {} failed: {}", mediaId, ex.toString(), ex);
        if (!rawReady) {
            try {
                ingestCleanupService.cleanupFailedIngest(mediaId, job.getId(), key, media.getExternalUrl(), ex);
            } catch (Exception cleanupEx) {
                LOGGER.warn("Cleanup after ingest failure failed mediaId={} err={}", mediaId, cleanupEx.toString());
            }
        }
        try {
            if (job.getMedia() != null) {
                mediaRepo.findById(job.getMedia().getId()).ifPresent(m -> {
                    m.setStatus(MediaStatus.FAILED);
                    mediaRepo.save(m);
                });
            }
        } catch (Exception ignore) {}
        jobService.markError(job.getId(), ex.getMessage(), Map.of("stack", stackTop(ex)));
        return false;
    }

}

    private boolean handleDetect(Job job) {
        UUID mediaId = mediaId(job);
        if (mediaId == null) {
            jobService.markError(job.getId(), "MEDIA_MISSING", Map.of());
            return false;
        }
        try {
            LOGGER.debug("detectWorkflow.start id={}", job.getId());
            int count = detectWorkflow.run(mediaId, job.getPayload());
            jobService.markDone(job.getId(), Map.of("segmentCount", count));
            return true;
        } catch (Exception e) {
            LOGGER.error("DETECT {} failed: {}", job.getId(), e.toString(), e);
            jobService.markError(job.getId(), e.getMessage(), Map.of("stack", stackTop(e)));
            return false;
        }
    }

    private boolean handleClipJob(Job job) {
        var clipId = UUID.fromString(String.valueOf(job.getPayload().get("clipId")));
        try {
            clipService.setStatus(clipId, ClipStatus.RENDERING);
            LOGGER.debug("clipWorkFlow.start id={}", job.getId());
            clipWorkFlow.run(clipId); // aparte bean → @Transactional actief
            clipService.setStatus(clipId, ClipStatus.READY);
            jobService.markDone(job.getId(), Map.of("clipId", clipId.toString()));
            return true;
        } catch (Exception e) {
            LOGGER.error("CLIP {} failed: {}", job.getId(), e.toString(), e);
            clipService.setStatus(clipId, ClipStatus.FAILED);
            jobService.markError(job.getId(), e.getMessage(), Map.of("stack", stackTop(e)));
            return false;
        }
    }

    private boolean handleExport(Job job) {
        try {
            LOGGER.debug("handle export start id={}", job.getId());
            renderService.handleExportJob(job);
            return true;
        } catch (Exception e) {
            LOGGER.error("EXPORT {} failed: {}", job.getId(), e.toString(), e);
            jobService.markError(job.getId(), e.getMessage(), Map.of("stack", stackTop(e)));
            return false;
        }
    }

    private Map<String, Object> forwardedDetectPayload(Job job, TranscriptionEngine.Result res) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (job != null && job.getPayload() != null) {
            Object detect = job.getPayload().get("detectPayload");
            if (detect instanceof Map<?, ?> detectMap) {
                detectMap.forEach((k, v) -> payload.put(String.valueOf(k), v));
            }
        }
        if (res != null) {
            payload.put("lang", res.lang());
            payload.put("provider", res.provider());
        }
        return payload.isEmpty() ? Map.of() : payload;
    }

    private void tryExtractThumbnail(ThumbnailService.ThumbnailRequest request, Path rawPath) {
        if (request == null || rawPath == null) {
            return;
        }
        try {
            thumbnailService.extractFromLocalMedia(request, rawPath);
        } catch (Exception ex) {
            LOGGER.warn("Thumbnail extract skipped media={} err={}", request.mediaId(), ex.toString());
        }
    }

    private Path preferredThumbnailSource(Path rawPath) {
        if (rawPath == null) {
            return null;
        }
        String name = rawPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".m4a")) {
            Path mp4Sibling = rawPath.getParent().resolve("source.mp4");
            if (Files.exists(mp4Sibling)) {
                return mp4Sibling;
            }
        }
        return rawPath;
    }

    @Transactional(readOnly = true)
    ThumbnailService.ThumbnailRequest buildThumbnailRequest(UUID mediaId) {
        Media media = mediaRepo.findByIdWithOwner(mediaId).orElseThrow();
        List<UUID> projectIds = projectMediaRepository.findProjectIdsByMediaId(mediaId);
        if (projectIds == null) {
            projectIds = List.of();
        }
        return new ThumbnailService.ThumbnailRequest(media.getId(), media.getOwner().getId(), projectIds, media.getDurationMs());
    }

    private String stackTop(Throwable ex) {
        var sw = new java.io.StringWriter();
        ex.printStackTrace(new java.io.PrintWriter(sw));
        // om payload compact te houden kun je eventueel alleen de eerste ~1–2 KB bewaren
        var s = sw.toString();
        return s.length() > 2000 ? s.substring(0, 2000) + "…(truncated)" : s;
    }

    @Transactional
    boolean handleCleanRender(Job job) {
        try {
            UUID clipId = UUID.fromString(String.valueOf(job.getPayload().get("clipId")));
            Clip clip = clipRepo.findById(clipId).orElseThrow();
            Media media = clip.getMedia();
            Path srcPath = storage.resolveRaw(media.getObjectKey());
            if (srcPath.getFileName().toString().toLowerCase().endsWith(".m4a")) {
                Path mp4Sibling = srcPath.getParent().resolve("source.mp4");
                if (java.nio.file.Files.exists(mp4Sibling)) {
                    srcPath = mp4Sibling;
                }
            }
            RenderOptions options = RenderOptions.withDefaults(Map.of(), null);
            RenderResult res = renderEngine.renderClean(srcPath, clip.getStartMs(), clip.getEndMs(), options);

            Asset clean = new Asset(media.getOwner(), AssetKind.CLIP_MP4_CLEAN, res.mp4Key(), res.mp4Size());
            clean.setRelatedClip(clip);
            clean.setRelatedMedia(media);
            assetRepo.save(clean);
            jobService.markDone(job.getId(), Map.of("clipId", clipId.toString(), "mp4Key", res.mp4Key()));
            return true;
        } catch (Exception e) {
            LOGGER.error("Clean render failed id={} reason={}", job.getId(), e.toString(), e);
            jobService.markError(job.getId(), e.getMessage(), Map.of("stack", stackTop(e)));
            return false;
        }
    }



    @Transactional
      void handleClip(Job job) throws Exception {
             //verwacht payload: clipId
            var clipId = UUID.fromString(String.valueOf(job.getPayload().get("clipId")));
            var clip = clipRepo.findById(clipId).orElseThrow();
            var media = clip.getMedia();
            var srcPath = storage.resolveRaw(media.getObjectKey());

            // subtitles (optioneel)

            var tr = transcriptRepo.findTopByMediaOrderByCreatedAtDesc(media).orElse(null);
            SubtitleFiles subs = null;
            if (tr != null) {
                subs = subtitles.buildSubtitles(tr, clip.getStartMs(), clip.getEndMs());
            }

            Map<String, Object> payload = job.getPayload();
            String requestedProfile = payload.get("profile") != null ? String.valueOf(payload.get("profile")) : RenderSpec.DEFAULT.profile();
            Boolean watermarkEnabled = payload.get("watermarkEnabled") == null ? RenderSpec.DEFAULT.watermarkEnabled() : Boolean.valueOf(String.valueOf(payload.get("watermarkEnabled")));
            String watermarkPath = payload.get("watermarkPath") != null ? String.valueOf(payload.get("watermarkPath")) : null;
            RenderSpec spec = new RenderSpec(RenderSpec.DEFAULT.width(), RenderSpec.DEFAULT.height(), RenderSpec.DEFAULT.fps(), RenderSpec.DEFAULT.crf(), RenderSpec.DEFAULT.preset(), requestedProfile, watermarkEnabled, watermarkPath);
            RenderOptions options = new RenderOptions(spec, clip.getMeta(), subs);
            var res = renderEngine.render(srcPath, clip.getStartMs(), clip.getEndMs(), options);

            // Registreer Assets
            var owner = media.getOwner();
            Asset mp4 = new Asset(owner, AssetKind.MP4, res.mp4Key(), res.mp4Size());
            mp4.setRelatedMedia(media);
            mp4.setRelatedClip(clip);
            assetRepo.save(mp4);

            if (res.thumbKey() != null) {
                Asset thumb = new Asset(owner, AssetKind.THUMBNAIL, res.thumbKey(), res.thumbSize());
                thumb.setRelatedMedia(media);
                thumb.setRelatedClip(clip);
                assetRepo.save(thumb);
            }

        if (subs != null) {
            if (subs.srtKey() != null) {
                Asset srt = new Asset(owner, AssetKind.SUB_SRT, subs.srtKey(), subs.srtSize());
                srt.setRelatedMedia(media);
                srt.setRelatedClip(clip);
                assetRepo.save(srt);
            }
            if (subs.vttKey() != null) {
                Asset vtt = new Asset(owner, AssetKind.SUB_VTT, subs.vttKey(), subs.vttSize());
                vtt.setRelatedMedia(media);
                vtt.setRelatedClip(clip);
                assetRepo.save(vtt);
            }
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
