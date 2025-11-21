package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.ClipResponse;
import com.example.clipbot_backend.dto.web.ClipCustomRequest;
import com.example.clipbot_backend.dto.web.ClipFromSegmentRequest;
import com.example.clipbot_backend.dto.web.EnqueueRenderRequest;
import com.example.clipbot_backend.model.Asset;
import com.example.clipbot_backend.model.Clip;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.repository.AssetRepository;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.service.AccountService;
import com.example.clipbot_backend.service.ClipService;
import com.example.clipbot_backend.service.JobService;
import com.example.clipbot_backend.util.ClipStatus;
import com.example.clipbot_backend.util.JobType;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/v1/clips")
public class ClipController {
    private final AccountService accountService;
    private final ClipService clipService;
    private final JobService jobService;
    private final AssetRepository assetRepo;
    private final MediaRepository mediaRepo;


    public ClipController(AccountService accountService, ClipService clipService, JobService jobService, AssetRepository assetRepo, MediaRepository mediaRepo) {
        this.accountService = accountService;
        this.clipService = clipService;
        this.jobService = jobService;
        this.assetRepo = assetRepo;
        this.mediaRepo = mediaRepo;
    }

    @PostMapping("/from-segment")
    public UUID createFromSegment(@Valid @RequestBody ClipFromSegmentRequest request) {
        if (request.title() != null && request.title().length() > 255)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TITLE_TOO_LONG");
        return clipService.createFromSegment(request.mediaId(), request.segmentId(), request.title(), request.meta());
    }

    @PostMapping("/custom")
    public UUID createCustom(@Valid @RequestBody ClipCustomRequest request) {
        if (request.title() != null && request.title().length() > 255)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TITLE_TOO_LONG");
        if (request.startMs() < 0 || request.endMs() <= request.startMs())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BOUNDS");
        return clipService.createCustom(request.mediaId(), request.startMs(), request.endMs(), request.title(), request.meta());
    }

    @Transactional(readOnly = true)
    @GetMapping("/{id}")
    public ClipResponse get(@PathVariable UUID id, @RequestParam String ownerExternalSubject) {
        var clip = clipService.get(id);
        ensureOwnedBy(clip, ownerExternalSubject);
        return ClipResponse.from(clip, assetRepo);
    }

    @Transactional(readOnly = true)
    @GetMapping("/media/{mediaId}")
    public Page<ClipResponse> listByMedia(@PathVariable UUID mediaId,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                          @RequestParam String ownerExternalSubject) {
        var media = mediaRepo.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));
        ensureOwnedBy(media, ownerExternalSubject);
        var entities = clipService.listByMedia(mediaId, PageRequest.of(page, size));
        return entities.map( clip -> ClipResponse.from(clip,assetRepo));
    }

    // Render-job enqueuen (CLIP) — geef juiste mediaId mee via de clip
    @PostMapping("/enqueue-render")
    public ResponseEntity<Map<String, UUID>> enqueueRender(@Valid @RequestBody EnqueueRenderRequest req,
                                                           @RequestParam String ownerExternalSubject) {
        var clip = clipService.get(req.clipId());
        ensureOwnedBy(clip, ownerExternalSubject);

        UUID jobId = clipService.enqueueRender(jobService, req.clipId()); // nooit null
        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
    }

    @PatchMapping("/{id}")
    public ClipResponse patch(@PathVariable UUID id,
                              @RequestParam String ownerExternalSubject,
                              @RequestBody Map<String,Object> body) {
        var clip = clipService.get(id);
        ensureOwnedBy(clip, ownerExternalSubject);
        String title = (String) body.get("title");
        Map<String,Object> meta = (Map<String,Object>) body.get("meta");
        ClipStatus status = null;
        if (body.get("status") != null) status = ClipStatus.valueOf(String.valueOf(body.get("status")));

        if (title != null && title.length() > 255) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TITLE_TOO_LONG");
        clip.setTitle(title != null ? title : clip.getTitle());
        if (meta != null) clip.setMeta(meta);
        if (status != null) clip.setStatus(status);
        clipService.save(clip); // voeg simpele save toe of hergebruik bestaande method
        return ClipResponse.from(clip, assetRepo);
    }


    @PostMapping("/{id}/status/{status}")
    public void setStatus(@PathVariable UUID id, @PathVariable ClipStatus status, @RequestParam String ownerExternalSubject) {
        var clip = clipService.get(id);
        ensureOwnedBy(clip, ownerExternalSubject);
        clipService.setStatus(id, status);
    }
    private void ensureOwnedBy(Clip clip, String sub) {
        if (isAdmin(sub)) return; // admin bypass
        var owner = clip.getMedia().getOwner().getExternalSubject();
        if (!Objects.equals(owner, sub))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CLIP_NOT_OWNED");
    }

    private void ensureOwnedBy(Media media, String sub) {
        if (isAdmin(sub)) return; // admin bypass
        var owner = media.getOwner().getExternalSubject();
        if (!Objects.equals(owner, sub))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "MEDIA_NOT_OWNED");
    }

    private boolean isAdmin(String sub) {
        try { return accountService.isAdmin(sub); } catch (Exception e) { return false; }
    }

}
