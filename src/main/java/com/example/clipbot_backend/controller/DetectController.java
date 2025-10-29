package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.SegmentDTO;
import com.example.clipbot_backend.dto.web.EnqueueRequest;
import com.example.clipbot_backend.dto.web.RunNowRequest;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.SegmentRepository;
import com.example.clipbot_backend.repository.TranscriptRepository;
import com.example.clipbot_backend.service.DetectionService;
import com.example.clipbot_backend.service.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.ACCEPTED;

@RestController
@RequestMapping("/v1/media/{mediaId}")
public class DetectController {
private final DetectionService detectionService;

public DetectController(DetectionService detectionService){
  this.detectionService = detectionService;
}
  @PostMapping("/detect")
  public ResponseEntity<Map<String,Object>> enqueue(@PathVariable UUID mediaId,
                                                    @RequestBody(required = false) EnqueueRequest req) {
    UUID jobId = detectionService.enqueueDetect(
            mediaId,
            req != null ? req.lang() : null,
            req != null ? req.provider() : null,
            req != null ? req.sceneThreshold() : null
    );
    return ResponseEntity.status(ACCEPTED).body(Map.of("jobId", jobId, "mediaId", mediaId, "status", "QUEUED"));
  }

  @PostMapping("/detect/now")
  public List<SegmentDTO> runNow(@PathVariable UUID mediaId, @RequestBody(required = false) RunNowRequest req) throws Exception {
    var opts = new DetectionService.DetectNowOptions(
            req != null ? req.lang() : null,
            req != null ? req.provider() : null,
            req != null ? req.maxCandidates() : null,
            req != null ? req.sceneThreshold() : null
    );
    return detectionService.runDetectNow(mediaId, opts);
  }

  @GetMapping("/segments")
  public List<DetectionService.PersistedSegmentView> list(@PathVariable UUID mediaId) {
    return detectionService.listSegments(mediaId);
  }

}
