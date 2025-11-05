package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.SegmentDTO;
import com.example.clipbot_backend.dto.web.SaveBatchRequest;
import com.example.clipbot_backend.model.Segment;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.service.SegmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/v1/segments")
@Validated
public class SegmentsController {

    private final SegmentService segmentService;
    private final MediaRepository mediaRepo;

    public SegmentsController(SegmentService segmentService, MediaRepository mediaRepo) {
        this.segmentService = segmentService;
        this.mediaRepo = mediaRepo;
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public void saveBatch(@RequestParam String ownerExternalSubject,
                          @RequestBody @Valid SaveBatchRequest request) {
        ensureOwnedBy(request.mediaId(), ownerExternalSubject);
        var items = request.items().stream().map(it -> {
            if (it.startMs() < 0 || it.endMs() <= it.startMs()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_SEGMENT_BOUNDS");
            }
            return new SegmentDTO(it.startMs(), it.endMs(), it.score(), it.meta());
        }).toList();
        segmentService.saveBatch(request.mediaId(), items);
    }

    @Transactional(readOnly = true)
    @GetMapping("/media/{mediaId}")
    public Page<Map<String,Object>> listByMedia(@PathVariable UUID mediaId,
                                                @RequestParam String ownerExternalSubject,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        ensureOwnedBy(mediaId, ownerExternalSubject);
        if (page < 0 || size <= 0 || size > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BAD_PAGINATION");
        }
        return segmentService.list(mediaId, PageRequest.of(page, size))
                .map(SegmentsController::toDto); // repo: ORDER BY start_ms ASC
    }

    @Transactional(readOnly = true)
    @GetMapping("/media/{mediaId}/top")
    public List<Map<String,Object>> topByScore(@PathVariable UUID mediaId,
                                               @RequestParam String ownerExternalSubject,
                                               @RequestParam(defaultValue = "5") @Min(1) int limit) {
        ensureOwnedBy(mediaId, ownerExternalSubject);
        int capped = Math.min(limit, 1000);
        return segmentService.topByScore(mediaId, capped).stream()
                .map(SegmentsController::toDto).toList();
    }

    @DeleteMapping("/media/{mediaId}")
    public void deleteByMedia(@PathVariable UUID mediaId,
                              @RequestParam String ownerExternalSubject) {
        ensureOwnedBy(mediaId, ownerExternalSubject);
        segmentService.deleteByMedia(mediaId);
    }

    private static Map<String,Object> toDto(Segment s){
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("startMs", s.getStartMs());
        m.put("endMs", s.getEndMs());
        m.put("score", s.getScore());
        m.put("meta", s.getMeta());
        return m;
    }

    private void ensureOwnedBy(UUID mediaId, String subject) {
        var media = mediaRepo.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));
        var ownerSub = media.getOwner().getExternalSubject();
        if (!Objects.equals(ownerSub, subject)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "MEDIA_NOT_OWNED");
        }
    }
}
