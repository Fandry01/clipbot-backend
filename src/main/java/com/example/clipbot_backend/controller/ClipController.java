package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.ClipResponse;
import com.example.clipbot_backend.dto.web.ClipCustomRequest;
import com.example.clipbot_backend.dto.web.ClipFromSegmentRequest;
import com.example.clipbot_backend.dto.web.EnqueueRenderRequest;
import com.example.clipbot_backend.model.Asset;
import com.example.clipbot_backend.model.Clip;
import com.example.clipbot_backend.repository.AssetRepository;
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
    private final AssetRepository assetRepo;


    public ClipController(ClipService clipService, JobService jobService, AssetRepository assetRepo) {
        this.clipService = clipService;
        this.jobService = jobService;
        this.assetRepo = assetRepo;
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
    public ClipResponse get(@PathVariable UUID id) {
        var clip = clipService.get(id);
        return ClipResponse.from(clip, assetRepo);
    }

    @GetMapping("/media/{mediaId}")
    public Page<ClipResponse> listByMedia(@PathVariable UUID mediaId,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size) {
        var entities = clipService.listByMedia(mediaId, PageRequest.of(page, size));
        return entities.map( clip -> ClipResponse.from(clip,assetRepo));
    }

    // Render-job enqueuen (CLIP) — geef juiste mediaId mee via de clip
    @PostMapping("/enqueue-render")
    public UUID enqueueRender(@Valid @RequestBody EnqueueRenderRequest request) {
        // retourneer desnoods jobId; hier doen we dat.
        return clipService.enqueueRender(jobService, request.clipId());
    }

    @PostMapping("/{id}/status/{status}")
    public void setStatus(@PathVariable UUID id, @PathVariable ClipStatus status) {
        clipService.setStatus(id, status);
    }
}
