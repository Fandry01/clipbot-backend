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
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
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

    @PostMapping("/from-url")
    public MediaFromUrlResponse createFromUrl(@Valid @RequestBody MediaFromUrlRequest request) {
        final String source = (request.source() == null || request.source().isBlank()) ? "url" : request.source();

        MetadataResult md = null;
        try { md = metadataService.resolve(request.url()); }
        catch (ResponseStatusException ex) { if (!"METADATA_FETCH_FAILED".equals(ex.getReason())) throw ex; }

        final String normalizedUrl = (md != null ? md.url() : metadataService.normalizeUrl(request.url()));
        final MediaPlatform platform = (md != null ? md.platform() : metadataService.detectPlatform(request.url()));
        final Long durationMs = (md != null && md.durationSec() != null) ? safeToMillis(md.durationSec()) : null;

        UUID mediaId = mediaService.createMediaFromUrl(
                request.ownerId(), normalizedUrl, platform, source, durationMs, request.objectKeyOverride()
        );

        // Service zet DOWNLOADING; dat moeten we zo teruggeven:
        return new MediaFromUrlResponse(
                mediaId,
                MediaStatus.DOWNLOADING.name(),
                (platform != null ? platform.id() : null),
                durationMs,
                (md != null ? md.thumbnail() : null),
                normalizedUrl
        );
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public MediaResponse get(@PathVariable UUID id){
        var media = mediaService.get(id);
        return new MediaResponse(
                media.getId(), media.getOwner().getId(), media.getObjectKey(), media.getDurationMs(),
                media.getStatus().name(), media.getSource(), media.getPlatform(), media.getExternalUrl()
        );
    }

    @GetMapping("/owner/{ownerId}")
    @Transactional(readOnly = true)
    public Page<MediaResponse> listByOwner(@PathVariable UUID ownerId,
                                           @RequestParam(defaultValue="0") int page,
                                           @RequestParam(defaultValue="10") int size){
        if (page < 0 || size <= 0 || size > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BAD_PAGINATION");
        return mediaService.listByOwner(ownerId, PageRequest.of(page, size))
                .map(m -> new MediaResponse(
                        m.getId(), m.getOwner().getId(), m.getObjectKey(), m.getDurationMs(),
                        m.getStatus().name(), m.getSource(), m.getPlatform(), m.getExternalUrl()
                ));
    }



    private static Long safeToMillis(Long sec) {
        try {
            return Math.multiplyExact(sec, 1000L);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }


}
