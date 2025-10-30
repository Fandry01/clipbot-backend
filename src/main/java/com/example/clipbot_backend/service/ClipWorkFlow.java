package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.RenderOptions;
import com.example.clipbot_backend.dto.SubtitleFiles;
import com.example.clipbot_backend.engine.Interfaces.ClipRenderEngine;
import com.example.clipbot_backend.model.*;

import com.example.clipbot_backend.repository.AssetRepository;
import com.example.clipbot_backend.repository.ClipRepository;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.TranscriptRepository;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.example.clipbot_backend.service.Interfaces.SubtitleService;
import com.example.clipbot_backend.util.AssetKind;
import com.example.clipbot_backend.util.ClipStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
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

    public ClipWorkFlow(ClipRepository clipRepo,
                        TranscriptRepository transcriptRepo,
                        StorageService storage,
                        ClipRenderEngine renderEngine,
                        AssetRepository assetRepo,
                        SubtitleService subtitles, MediaRepository mediaRepo) {
        this.clipRepo = clipRepo;
        this.transcriptRepo = transcriptRepo;
        this.storage = storage;
        this.renderEngine = renderEngine;
        this.assetRepo = assetRepo;
        this.subtitles = subtitles;
        this.mediaRepo = mediaRepo;
    }

    @Transactional
    public void run(UUID clipId) throws Exception {
        // 1) Clip + Media ophalen binnen TX ( voorkomt LazyInitializationException )
        Clip clip = clipRepo.findById(clipId).orElseThrow();
        Media media = clip.getMedia();
        clip.setStatus(ClipStatus.RENDERING);
        clipRepo.saveAndFlush(clip);

        try {
            // 2) Bronpad bepalen
            Path srcPath = storage.resolveRaw(media.getObjectKey());
            if (srcPath == null || !Files.exists(srcPath)) {
                throw new IllegalStateException("RAW missing: " + media.getObjectKey());
            }

            // 3) Laatste transcript (ongeacht provider) → subs
            Transcript tr = transcriptRepo.findTopByMediaOrderByCreatedAtDesc(media).orElse(null);
            SubtitleFiles subs = null;
            if (tr != null) {
                subs = subtitles.buildSubtitles(tr, clip.getStartMs(), clip.getEndMs());
            }

            // 4) Render
            RenderOptions options = RenderOptions.withDefaults(clip.getMeta(), subs);
            var res = renderEngine.render(srcPath, clip.getStartMs(), clip.getEndMs(), options);

            // 5) Assets registreren
            var clipRef = clipRepo.getReferenceById(clip.getId());
            var mediaRef = mediaRepo.getReferenceById(media.getId());
            Account owner = media.getOwner();

            Asset mp4 = new Asset(owner, AssetKind.CLIP_MP4, res.mp4Key(), res.mp4Size());
            mp4.setRelatedClip(clipRef);
            mp4.setRelatedMedia(mediaRef);
            assetRepo.save(mp4);

            if (res.thumbKey() != null) {
                Asset thumb = new Asset(owner, AssetKind.THUMBNAIL, res.thumbKey(), res.thumbSize());
                thumb.setRelatedClip(clipRef);      // ← nodig voor /v1/assets/latest/clip
                thumb.setRelatedMedia(mediaRef);
                assetRepo.saveAndFlush(thumb);
            }

            if (subs != null) {
                if (subs.srtKey() != null) {
                    Asset srt = new Asset(owner, AssetKind.SUB_SRT, subs.srtKey(), subs.srtSize());
                    srt.setRelatedClip(clipRef);
                    srt.setRelatedMedia(mediaRef);
                    assetRepo.saveAndFlush(srt);
                }

                if (subs.vttKey() != null) {
                    Asset vtt = new Asset(owner, AssetKind.SUB_VTT, subs.vttKey(), subs.vttSize());
                    vtt.setRelatedClip(clipRef);
                    vtt.setRelatedMedia(mediaRef);
                    assetRepo.saveAndFlush(vtt);
                }
            }

            // 6) Clip bijwerken
            clip.setCaptionSrtKey(subs != null ? subs.srtKey() : null);
            clip.setCaptionVttKey(subs != null ? subs.vttKey() : null);
            clip.setStatus(ClipStatus.READY);
            clipRepo.save(clip);
        } catch (Exception e) {
            // status op FAILED bij crash
            clip.setStatus(ClipStatus.FAILED);
            // optioneel: error in meta stoppen
            Map<String, Object> m = new java.util.LinkedHashMap<>(clip.getMeta() == null ? Map.of() : clip.getMeta());
            m.put("renderError", e.toString());
            clip.setMeta(m);
            clipRepo.save(clip);
            throw e;
        }
    }
}

