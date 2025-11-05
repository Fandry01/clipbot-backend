package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.SegmentDTO;
import com.example.clipbot_backend.dto.web.EnqueueRequest;
import com.example.clipbot_backend.dto.web.RunNowRequest;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.SegmentRepository;
import com.example.clipbot_backend.repository.TranscriptRepository;
import com.example.clipbot_backend.service.DetectionService;
import com.example.clipbot_backend.service.JobService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;


@RestController
@RequestMapping("/v1/media/{mediaId}")
@Validated
public class DetectController {

  private final DetectionService detectionService;
  private final MediaRepository mediaRepo;

  public DetectController(DetectionService detectionService, MediaRepository mediaRepo) {
    this.detectionService = detectionService;
    this.mediaRepo = mediaRepo;
  }

  @PostMapping("/detect")
  public ResponseEntity<Map<String,Object>> enqueue(@PathVariable UUID mediaId,
                                                    @RequestParam String ownerExternalSubject,
                                                    @RequestBody(required = false) EnqueueRequest req) {
    ensureOwnedBy(mediaId, ownerExternalSubject);

    Double scene = req != null ? req.sceneThreshold() : null;
    if (scene != null && (scene < 0.0 || scene > 1.0))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_SCENE_THRESHOLD");

    UUID jobId = detectionService.enqueueDetect(
            mediaId,
            nvl(req == null ? null : emptyToNull(req.lang())),
            nvl(req == null ? null : emptyToNull(req.provider())),
            scene
    );

    return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(Map.of("jobId", jobId, "mediaId", mediaId,"status","QUEUED"));
  }

  @PostMapping("/detect/now")
  public List<SegmentDTO> runNow(@PathVariable UUID mediaId,
                                 @RequestParam String ownerExternalSubject,
                                 @RequestBody(required = false) RunNowRequest req) throws Exception {
    ensureOwnedBy(mediaId, ownerExternalSubject);

    Integer max = req != null ? req.maxCandidates() : null;
    if (max != null && (max <= 0 || max > 64))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_MAX_CANDIDATES");

    Double scene = req != null ? req.sceneThreshold() : null;
    if (scene != null && (scene < 0.0 || scene > 1.0))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_SCENE_THRESHOLD");

    var opts = new DetectionService.DetectNowOptions(
            nvl(req == null ? null : emptyToNull(req.lang())),
            nvl(req == null ? null : emptyToNull(req.provider())),
            max,
            scene
    );
    return detectionService.runDetectNow(mediaId, opts);
  }

  @GetMapping("/segments")
  @Transactional(readOnly = true)
  public List<DetectionService.PersistedSegmentView> list(@PathVariable UUID mediaId,
                                                          @RequestParam String ownerExternalSubject,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "50") int size) {
    ensureOwnedBy(mediaId, ownerExternalSubject);
    return detectionService.listSegments(mediaId,page,size); // paginatie optioneel toevoegen
  }

  private void ensureOwnedBy(UUID mediaId, String subject) {
    var media = mediaRepo.findById(mediaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));
    var ownerSub = media.getOwner().getExternalSubject();
    if (!Objects.equals(ownerSub, subject))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "MEDIA_NOT_OWNED");
  }

  private static String emptyToNull(String s) { return (s == null || s.isBlank()) ? null : s; }
  private static String nvl(String s) {
    if (s == null) return null;
    s = s.trim();
    return s.isEmpty() ? null : s.toLowerCase(Locale.ROOT); } // hier kun je evt. normaliseren (lowercase) als gewenst
}
