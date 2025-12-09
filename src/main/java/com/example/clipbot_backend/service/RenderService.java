package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.RenderSpec;
import com.example.clipbot_backend.dto.RenderResult;
import com.example.clipbot_backend.dto.render.SubtitleStyle;
import com.example.clipbot_backend.engine.Interfaces.ClipRenderEngine;
import com.example.clipbot_backend.model.Asset;
import com.example.clipbot_backend.model.Clip;
import com.example.clipbot_backend.repository.AssetRepository;
import com.example.clipbot_backend.repository.ClipRepository;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.example.clipbot_backend.util.AssetKind;
import com.example.clipbot_backend.util.JobType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class RenderService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderService.class);

    private final ClipRepository clipRepo;
    private final AssetRepository assetRepo;
    private final JobService jobService;
    private final StorageService storageService;
    private final ClipRenderEngine renderEngine;
    private final ObjectMapper objectMapper;

    public RenderService(ClipRepository clipRepo,
                         AssetRepository assetRepo,
                         JobService jobService,
                         StorageService storageService,
                         ClipRenderEngine renderEngine,
                         ObjectMapper objectMapper) {
        this.clipRepo = clipRepo;
        this.assetRepo = assetRepo;
        this.jobService = jobService;
        this.storageService = storageService;
        this.renderEngine = renderEngine;
        this.objectMapper = objectMapper;
    }

    public UUID enqueueExportWithStyle(UUID clipId, SubtitleStyle style, String profile) {
        Objects.requireNonNull(clipId, "clipId");
        Clip clip = clipRepo.findById(clipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CLIP_NOT_FOUND"));
        ensureSubtitlesAvailable(clip);

        SubtitleStyle effective = style == null ? SubtitleStyle.defaults() : style;
        Map<String, Object> payload = new HashMap<>();
        payload.put("clipId", clipId.toString());
        payload.put("subtitleStyle", effective);
        if (profile != null && !profile.isBlank()) {
            payload.put("profile", profile);
        }
        UUID mediaId = clip.getMedia() != null ? clip.getMedia().getId() : null;
        return jobService.enqueue(mediaId, JobType.EXPORT, payload);
    }

    @Transactional
    public void handleExportJob(com.example.clipbot_backend.model.Job job) {
        try {
            UUID clipId = UUID.fromString(String.valueOf(job.getPayload().get("clipId")));
            Clip clip = clipRepo.findById(clipId)
                    .orElseThrow(() -> new IllegalStateException("Clip not found for export: " + clipId));

            SubtitleStyle style = Optional.ofNullable(job.getPayload().get("subtitleStyle"))
                    .map(payload -> objectMapper.convertValue(payload, SubtitleStyle.class))
                    .orElse(SubtitleStyle.defaults());

            String profile = Optional.ofNullable(job.getPayload().get("profile"))
                    .map(Object::toString)
                    .orElse(null);

            Path subtitlePath = resolveSubtitlePath(clip);
            Optional<Path> cleanPath = resolveCleanPath(clip);
            Path inputPath = cleanPath.orElseGet(() -> resolveRawPath(clip));
            long startMs = cleanPath.isPresent() ? 0L : clip.getStartMs();
            long endMs = cleanPath.isPresent() ? clip.getEndMs() - clip.getStartMs() : clip.getEndMs();
            RenderSpec spec = profile != null
                    ? new RenderSpec(RenderSpec.DEFAULT.width(), RenderSpec.DEFAULT.height(), RenderSpec.DEFAULT.fps(),
                    RenderSpec.DEFAULT.crf(), RenderSpec.DEFAULT.preset(), profile, Boolean.FALSE, null)
                    : RenderSpec.DEFAULT;

            RenderResult result = renderEngine.renderStyled(inputPath, subtitlePath, startMs, endMs, spec, style);
            persistExportAsset(clip, result);
            jobService.markDone(job.getId(), Map.of("mp4Key", result.mp4Key()));
        } catch (Exception e) {
            LOGGER.error("Export job failed id={} reason={}", job.getId(), e.toString(), e);
            jobService.markError(job.getId(), e.getMessage(), Map.of("stack", stackTop(e)));
        }
    }

    private void ensureSubtitlesAvailable(Clip clip) {
        boolean hasSubs = assetRepo.findTopByRelatedClipAndKindOrderByCreatedAtDesc(clip, AssetKind.SUB_VTT).isPresent()
                || assetRepo.findTopByRelatedClipAndKindOrderByCreatedAtDesc(clip, AssetKind.SUB_SRT).isPresent();
        if (!hasSubs) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SUBTITLES_NOT_AVAILABLE");
        }
    }

    private Path resolveSubtitlePath(Clip clip) {
        var vtt = assetRepo.findTopByRelatedClipAndKindOrderByCreatedAtDesc(clip, AssetKind.SUB_VTT);
        var srt = assetRepo.findTopByRelatedClipAndKindOrderByCreatedAtDesc(clip, AssetKind.SUB_SRT);
        Asset sub = vtt.orElseGet(() -> srt.orElse(null));
        if (sub == null) {
            throw new IllegalStateException("No subtitles asset for clip " + clip.getId());
        }
        Path path = storageService.resolveOut(sub.getObjectKey());
        if (!Files.exists(path)) {
            throw new IllegalStateException("Subtitle file missing: " + sub.getObjectKey());
        }
        return path;
    }

    private Path resolveInputPath(Clip clip) {
        return resolveCleanPath(clip).orElseGet(() -> resolveRawPath(clip));
    }

    private Optional<Path> resolveCleanPath(Clip clip) {
        return assetRepo.findTopByRelatedClipAndKindOrderByCreatedAtDesc(clip, AssetKind.CLIP_MP4_CLEAN)
                .map(asset -> storageService.resolveOut(asset.getObjectKey()))
                .filter(Files::exists);
    }

    private Path resolveRawPath(Clip clip) {
        Path raw = storageService.resolveRaw(clip.getMedia().getObjectKey());
        if (!Files.exists(raw)) {
            throw new IllegalStateException("Raw input missing for clip " + clip.getId());
        }
        if (raw.getFileName().toString().toLowerCase().endsWith(".m4a")) {
            Path mp4Sibling = raw.getParent().resolve("source.mp4");
            if (Files.exists(mp4Sibling)) {
                return mp4Sibling;
            }
        }
        return raw;
    }

    private void persistExportAsset(Clip clip, RenderResult result) {
        var ownerRef = clip.getMedia().getOwner();
        var clipRef = clipRepo.getReferenceById(clip.getId());
        var mediaRef = clip.getMedia();
        Asset asset = new Asset(ownerRef, AssetKind.MP4, result.mp4Key(), result.mp4Size());
        asset.setRelatedClip(clipRef);
        asset.setRelatedMedia(mediaRef);
        assetRepo.save(asset);
    }

    private String stackTop(Throwable ex) {
        var sw = new java.io.StringWriter();
        ex.printStackTrace(new java.io.PrintWriter(sw));
        var s = sw.toString();
        return s.length() > 2000 ? s.substring(0, 2000) + "…(truncated)" : s;
    }
}
