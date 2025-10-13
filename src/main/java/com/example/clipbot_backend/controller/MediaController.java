package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.web.MediaCreateRequest;
import com.example.clipbot_backend.dto.web.MediaFromUrlRequest;
import com.example.clipbot_backend.dto.web.MediaFromUrlResponse;
import com.example.clipbot_backend.dto.web.MediaResponse;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.service.Interfaces.MediaService;
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
        var source = (request.source() == null || request.source().isBlank()) ? "url" : request.source();
        MetadataResult metadata;
        try {
            metadata = metadataService.resolve(request.url());
        } catch (ResponseStatusException ex) {
            if ("METADATA_FETCH_FAILED".equals(ex.getReason())) {
                MediaPlatform platform = metadataService.detectPlatform(request.url());
                String normalizedUrl = metadataService.normalizeUrl(request.url());
                var mediaId = mediaService.createMediaFromUrl(request.ownerId(), normalizedUrl, platform, source, null);
                return new MediaFromUrlResponse(mediaId, MediaStatus.REGISTERED.name(), platform.id(), null, null);
            }
            throw ex;
        }

        Long durationMs = null;
        if (metadata.durationSec() != null) {
            try {
                durationMs = Math.multiplyExact(metadata.durationSec(), 1000L);
            } catch (ArithmeticException ignored) {
                durationMs = Long.MAX_VALUE;
            }
        }
        var mediaId = mediaService.createMediaFromUrl(request.ownerId(), metadata.url(), metadata.platform(), source, durationMs);
        return new MediaFromUrlResponse(mediaId, MediaStatus.REGISTERED.name(), metadata.platform().id(), durationMs, null);
    }

    @GetMapping("/owner/{ownerId}")
    public Page<Media> listByOwner(@PathVariable UUID ownerId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size ){
        return mediaService.listByOwner(ownerId, PageRequest.of(page, size));
    }

}
