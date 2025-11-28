package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.RenderOptions;
import com.example.clipbot_backend.dto.RenderResult;
import com.example.clipbot_backend.dto.SubtitleFiles;
import com.example.clipbot_backend.engine.Interfaces.ClipRenderEngine;
import com.example.clipbot_backend.model.*;

import com.example.clipbot_backend.repository.*;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.example.clipbot_backend.service.Interfaces.SubtitleService;
import com.example.clipbot_backend.service.thumbnail.ThumbnailService;
import com.example.clipbot_backend.util.AssetKind;
import com.example.clipbot_backend.util.ClipStatus;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ClipWorkFlow {

    private final ClipRepository clipRepo;
    private final TranscriptRepository transcriptRepo;
    private final StorageService storage;
    private final ClipRenderEngine renderEngine;
    private final AssetRepository assetRepo;
    private final SubtitleService subtitles;
    private final MediaRepository mediaRepo;
    private final AccountRepository accountRepo;
    private final ProjectMediaRepository projectMediaRepository;
    private final ThumbnailService thumbnailService;
    private TransactionTemplate txReqNew;

    public ClipWorkFlow(ClipRepository clipRepo,
                        TranscriptRepository transcriptRepo,
                        StorageService storage,
                        ClipRenderEngine renderEngine,
                        AssetRepository assetRepo,
                        SubtitleService subtitles, MediaRepository mediaRepo, AccountRepository accountRepo, ProjectMediaRepository projectMediaRepository, ThumbnailService thumbnailService, TransactionTemplate txReqNew) {
        this.clipRepo = clipRepo;
        this.transcriptRepo = transcriptRepo;
        this.storage = storage;
        this.renderEngine = renderEngine;
        this.assetRepo = assetRepo;
        this.subtitles = subtitles;
        this.mediaRepo = mediaRepo;
        this.accountRepo = accountRepo;
        this.projectMediaRepository = projectMediaRepository;
        this.thumbnailService = thumbnailService;
        this.txReqNew = txReqNew;
    }

 // TX A (kort)
    public void markRendering(UUID clipId) {
        txReqNew.execute(status -> {
            var clip = clipRepo.findById(clipId).orElseThrow();
            clip.setStatus(ClipStatus.RENDERING);
            clipRepo.saveAndFlush(clip);
            return null;
        });
    }


    public void persistSuccess(IoData ioData, RenderResult res, @Nullable SubtitleFiles subs, @Nullable RenderResult clean) {
        txReqNew.execute(status -> {
            var clipRef = clipRepo.getReferenceById(ioData.clipId);
            var mediaRef = mediaRepo.getReferenceById(ioData.mediaId);
            var ownerRef = accountRepo.getReferenceById(ioData.ownerId);

            // (optioneel) assets opschonen per kind
            // assetRepo.deleteByRelatedClipAndKind(clipRef, AssetKind.MP4); ...

            Asset mp4 = new Asset(ownerRef, AssetKind.MP4, res.mp4Key(), ensureSize(res.mp4Key(), res.mp4Size()));
            mp4.setRelatedClip(clipRef);
            mp4.setRelatedMedia(mediaRef);
            assetRepo.save(mp4);

            if (res.thumbKey() != null) {
                Asset thumb = new Asset(ownerRef, AssetKind.THUMBNAIL, res.thumbKey(), ensureSize(res.thumbKey(), res.thumbSize()));
                thumb.setRelatedClip(clipRef);
                thumb.setRelatedMedia(mediaRef);
                assetRepo.save(thumb);
                projectMediaRepository.findByMedia(mediaRef)
                        .forEach(link -> thumbnailService.applyFallbackIfEligible(link.getProject(), res.thumbKey()));
            }

            if (subs != null) {
                if (subs.srtKey() != null) {
                    Asset srt = new Asset(ownerRef, AssetKind.SUB_SRT, subs.srtKey(), ensureSize(subs.srtKey(), subs.srtSize()));
                    srt.setRelatedClip(clipRef);
                    srt.setRelatedMedia(mediaRef);
                    assetRepo.save(srt);
                }
                if (subs.vttKey() != null) {
                    Asset vtt = new Asset(ownerRef, AssetKind.SUB_VTT, subs.vttKey(), ensureSize(subs.vttKey(), subs.vttSize()));
                    vtt.setRelatedClip(clipRef);
                    vtt.setRelatedMedia(mediaRef);
                    assetRepo.save(vtt);
                }
            }

            if (clean != null) {
                Asset cleanMp4 = new Asset(ownerRef, AssetKind.CLIP_MP4_CLEAN, clean.mp4Key(), ensureSize(clean.mp4Key(), clean.mp4Size()));
                cleanMp4.setRelatedClip(clipRef);
                cleanMp4.setRelatedMedia(mediaRef);
                assetRepo.save(cleanMp4);
            }

            // deprecate: clip.setCaptionSrtKey(...)
            clipRef.setStatus(ClipStatus.READY);
            clipRepo.save(clipRef);

            // (optioneel) MediaStatusService.trySetReady(media) als voorwaarden kloppen
            return null;
        });
    }


    public void persistFailure(UUID clipId, Exception e) {
        txReqNew.execute(status -> {
        var clip = clipRepo.findById(clipId).orElseThrow();
        clip.setStatus(ClipStatus.FAILED);
        var m = new LinkedHashMap<String,Object>(clip.getMeta() == null ? Map.of() : clip.getMeta());
        m.put("renderError", e.toString());
        clip.setMeta(m);
        clipRepo.save(clip);

            return null;
        });
    }


    public void run(UUID clipId) throws Exception {
        markRendering(clipId);                // TX A

        IoData io = loadIoData(clipId);
        // TX (read-only) haalt alles gefetch’d op

        // IO/Render zonder TX
        Path srcPath = storage.resolveRaw(io.objectKey());
        if (io.objectKey().toLowerCase(Locale.ROOT).endsWith(".m4a")) {
            Path mp4Sibling = srcPath.getParent().resolve("source.mp4");
            if (Files.exists(mp4Sibling) && Files.isRegularFile(mp4Sibling)) {
                srcPath = mp4Sibling;
            }
        }
        if (!Files.exists(srcPath) || !Files.isRegularFile(srcPath)) {
            throw new IllegalStateException("RAW missing: " + srcPath);
        }

        // transcript ophalen met mediaId-variant voorkomt lazy issues
        var tr = transcriptRepo.findTopByMediaIdOrderByCreatedAtDesc(io.mediaId()).orElse(null);

        SubtitleFiles subs = (tr != null)
                ? subtitles.buildSubtitles(tr, io.startMs(), io.endMs())
                : null;

        RenderOptions options = RenderOptions.withDefaults(Map.of(), subs);
        RenderResult res = renderEngine.render(srcPath, io.startMs(), io.endMs(), options);
        RenderResult clean = null;
        try {
            clean = renderEngine.renderClean(srcPath, io.startMs(), io.endMs(), RenderOptions.withDefaults(Map.of(), null));
        } catch (Exception e) {
            LOGGER.warn("Clean render failed for clip {}: {}", clipId, e.toString());
        }
        // validateOutputs(res, subs);

        try {
            persistSuccess(io, res, subs, clean); // TX B
        } catch (Exception e) {
            persistFailure(clipId, e);         // TX C
            throw e;
        }
    }

    private long ensureSize(String key, long known) {
        if (known > 0) return known;
        try { return Files.size(storage.resolveOut(key)); }
        catch (IOException e) { throw new IllegalStateException("sizeOf failed: " + key, e); }
    }

    private void validateOutputs(RenderResult res, @Nullable SubtitleFiles subs) throws IOException {
        if (res == null || res.mp4Key() == null || res.mp4Key().isBlank())
            throw new IllegalStateException("Render produced no mp4Key");
        Path mp4 = storage.resolveOut(res.mp4Key());
        if (!Files.exists(mp4) || Files.size(mp4) <= 0)
            throw new IllegalStateException("MP4 missing/empty: " + res.mp4Key());

        if (res.thumbKey() != null) {
            Path t = storage.resolveOut(res.thumbKey());
            if (!Files.exists(t) || Files.size(t) <= 0)
                throw new IllegalStateException("Thumb missing/empty: " + res.thumbKey());
        }
        if (subs != null) {
            if (subs.srtKey() != null) {
                Path s = storage.resolveOut(subs.srtKey());
                if (!Files.exists(s) || Files.size(s) <= 0)
                    throw new IllegalStateException("SRT missing/empty: " + subs.srtKey());
            }
            if (subs.vttKey() != null) {
                Path v = storage.resolveOut(subs.vttKey());
                if (!Files.exists(v) || Files.size(v) <= 0)
                    throw new IllegalStateException("VTT missing/empty: " + subs.vttKey());
            }
        }
    }
    private record IoData(UUID clipId, UUID mediaId, UUID ownerId, String objectKey, long startMs, long endMs) {}

    @Transactional(readOnly = true)
    protected IoData loadIoData(UUID clipId) {
        var clip = clipRepo.findByIdWithMedia(clipId)
                .orElseThrow(() -> new IllegalStateException("CLIP_NOT_FOUND"));
        var m = clip.getMedia(); // is gefetched, dus geen lazy-issue
        return new IoData(clip.getId(), m.getId(),m.getOwner().getId(), m.getObjectKey(), clip.getStartMs(), clip.getEndMs());
    }

}

