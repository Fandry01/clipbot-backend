package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.AssetResponse;
import com.example.clipbot_backend.model.Asset;
import com.example.clipbot_backend.repository.AssetRepository;
import com.example.clipbot_backend.repository.ClipRepository;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.util.AssetKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


import java.util.UUID;

@RestController
@RequestMapping("/v1/assets")
public class AssetController {

    private final AssetRepository assetRepo;
    private final MediaRepository mediaRepo;
    private final ClipRepository clipRepo;

    public AssetController(AssetRepository assetRepo, MediaRepository mediaRepo, ClipRepository clipRepo) {
        this.assetRepo = assetRepo;
        this.mediaRepo = mediaRepo;
        this.clipRepo = clipRepo;
    }

    @GetMapping("/by-media/{mediaId}")
    public Page<AssetResponse> byMedia(@PathVariable UUID mediaId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestParam(required = false) AssetKind kind) {
        var media = mediaRepo.findById(mediaId).orElseThrow();

        Page<Asset> p = (kind == null)
                ? assetRepo.findByRelatedMediaOrderByCreatedAtDesc(media, PageRequest.of(page, size))
                : assetRepo.findByRelatedMediaAndKindOrderByCreatedAtDesc(media, kind, PageRequest.of(page, size));

        return p.map(AssetResponse::from);
    }

    @GetMapping("/by-clip/{clipId}")
    public Page<AssetResponse> byClip(@PathVariable UUID clipId,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "10") int size,
                                      @RequestParam(required = false) AssetKind kind) {
        var clip = clipRepo.findById(clipId).orElseThrow();

        Page<Asset> p = (kind == null)
                ? assetRepo.findByRelatedClipOrderByCreatedAtDesc(clip, PageRequest.of(page, size))
                : assetRepo.findByRelatedClipAndKindOrderByCreatedAtDesc(clip, kind, PageRequest.of(page, size));

        return p.map(AssetResponse::from);
    }

    // Shortcut: nieuwste MP4 voor een clip
    @GetMapping("/latest/clip/{clipId}")
    public ResponseEntity<AssetResponse> latestForClip(@PathVariable UUID clipId,
                                                       @RequestParam(defaultValue = "CLIP_MP4") AssetKind kind) {
        var clip = clipRepo.findById(clipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clip not found: " + clipId));

        return assetRepo.findTopByRelatedClipAndKindOrderByCreatedAtDesc(clip, kind)
                .map(a -> ResponseEntity.ok(AssetResponse.from(a)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Shortcut: nieuwste MP4 voor media (bv. als je 1 clip per media maakt)
    @GetMapping("/latest/media/{mediaId}")
    public ResponseEntity<AssetResponse> latestForMedia(@PathVariable UUID mediaId,
                                                        @RequestParam(defaultValue = "CLIP_MP4") AssetKind kind) {
        var media = mediaRepo.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found: " + mediaId));

        return assetRepo.findTopByRelatedMediaAndKindOrderByCreatedAtDesc(media, kind)
                .map(a -> ResponseEntity.ok(AssetResponse.from(a)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }


}

