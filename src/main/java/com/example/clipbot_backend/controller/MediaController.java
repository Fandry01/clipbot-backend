package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.web.MediaCreateRequest;
import com.example.clipbot_backend.dto.web.MediaFromUrlRequest;
import com.example.clipbot_backend.dto.web.MediaFromUrlResponse;
import com.example.clipbot_backend.dto.web.MediaResponse;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.service.MediaService;
import com.example.clipbot_backend.service.metadata.MetadataResult;
import com.example.clipbot_backend.service.metadata.MetadataService;
import com.example.clipbot_backend.util.MediaPlatform;
import com.example.clipbot_backend.util.MediaStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/v1/media")
public class MediaController {
    private final MediaService mediaService;
    private final MetadataService metadataService;

    public MediaController(MediaService mediaService, MetadataService metadataService) {
        this.mediaService = mediaService;
        this.metadataService = metadataService;
    }

    @PostMapping
    public UUID create(@Valid @RequestBody MediaCreateRequest request){
        return mediaService.createMedia(request.ownerId(), request.objectKey(), request.source() == null ?
                "upload" : request.source());
    }

    @GetMapping("/{id}")
    public MediaResponse get(@PathVariable UUID id){
        var media = mediaService.get(id);
        return new MediaResponse(media.getId(), media.getOwner().getId(), media.getObjectKey(), media.getDurationMs(),
                media.getStatus().name(), media.getSource());
    }

    @PostMapping("/from-url")
    public MediaFromUrlResponse createFromUrl(@Valid @RequestBody MediaFromUrlRequest request) {
        final String source = (request.source() == null || request.source().isBlank()) ? "url" : request.source();

        // 1) Probeer metadata op te halen
        MetadataResult md = null;
        try {
            md = metadataService.resolve(request.url());
        } catch (ResponseStatusException ex) {
            // Alleen fallbacken als het echt een fetch-issue was; anders hergooien
            if (!"METADATA_FETCH_FAILED".equals(ex.getReason())) throw ex;
        }

        // 2) Normalize + platform + duration
        final String normalizedUrl = (md != null ? md.url() : metadataService.normalizeUrl(request.url()));
        final MediaPlatform platform = (md != null ? md.platform() : metadataService.detectPlatform(request.url()));
        final Long durationMs = (md != null && md.durationSec() != null)
                ? safeToMillis(md.durationSec())
                : null;

        // 3) Maak media aan (status = UPLOADED in service)
        UUID mediaId = mediaService.createMediaFromUrl(
                request.ownerId(),
                normalizedUrl,
                platform,
                source,
                durationMs,
                request.objectKeyOverride() // mag null zijn
        );

        // 4) Response terug (platform-id + evt. duration/thumbnail)
        assert md != null;
        return new MediaFromUrlResponse(
                mediaId,
                MediaStatus.UPLOADED.name(),            // conform CHECK constraint
                platform.id(),
                durationMs,
                (md != null ? md.thumbnail() : null),
                md.thumbnail()
        );
    }
    private static Long safeToMillis(Long sec) {
        try {
            return Math.multiplyExact(sec, 1000L);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    @GetMapping("/owner/{ownerId}")
    public Page<Media> listByOwner(@PathVariable UUID ownerId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size ){
        return mediaService.listByOwner(ownerId, PageRequest.of(page, size));
    }

}
