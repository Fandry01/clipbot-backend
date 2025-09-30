package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.web.ClipCustomRequest;
import com.example.clipbot_backend.dto.web.ClipFromSegmentRequest;
import com.example.clipbot_backend.dto.web.EnqueueRenderRequest;
import com.example.clipbot_backend.service.ClipService;
import com.example.clipbot_backend.service.JobService;
import com.example.clipbot_backend.util.ClipStatus;
import com.example.clipbot_backend.util.JobType;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/clips")
public class ClipController {
    private final ClipService clipService;
    private final JobService jobService;

    public ClipController(ClipService clipService, JobService jobService) {
        this.clipService = clipService;
        this.jobService = jobService;
    }

    @PostMapping("/from-segment")
    public UUID createFromSegment(@Valid @RequestBody ClipFromSegmentRequest request) {
        return clipService.createFromSegment(request.mediaId(), request.segmentId(), request.title(), request.meta());
    }
    @PostMapping("/custom")
    public UUID createCustom(@Valid @RequestBody ClipCustomRequest request) {
        return clipService.createCustom(request.mediaId(), request.startMs(), request.endMs(), request.title(), request.meta());
    }

    @GetMapping("/{id}")
    public Object get(@PathVariable UUID id) { return clipService.get(id); }

    @GetMapping("/media/{mediaId}")
    public Page<?> listByMedia(@PathVariable UUID mediaId,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size) {
        return clipService.listByMedia(mediaId, PageRequest.of(page, size));
    }

    // Render-job enqueuen (CLIP)
    @PostMapping("/enqueue-render")
    public UUID enqueueRender(@Valid @RequestBody EnqueueRenderRequest request) {
        return jobService.enqueue(null, JobType.CLIP, Map.of("clipId", request.clipId().toString()));
    }

    @PostMapping("/{id}/status/{status}")
    public void setStatus(@PathVariable UUID id, @PathVariable ClipStatus status) {
        clipService.setStatus(id, status);
    }

}
