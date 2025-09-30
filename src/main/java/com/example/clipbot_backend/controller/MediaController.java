package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.web.MediaCreateRequest;
import com.example.clipbot_backend.dto.web.MediaResponse;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.service.Interfaces.MediaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.UUID;

@RestController
@RequestMapping("/v1/media")
public class MediaController {
    private final MediaService mediaService;
    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
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

    @GetMapping("/owner/{ownerId}")
    public Page<Media> listByOwner(@PathVariable UUID ownerId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size ){
        return mediaService.listByOwner(ownerId, PageRequest.of(page, size));
    }

}
