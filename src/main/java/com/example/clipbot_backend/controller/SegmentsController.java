package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.SegmentDTO;
import com.example.clipbot_backend.dto.web.SaveBatchRequest;
import com.example.clipbot_backend.model.Segment;
import com.example.clipbot_backend.service.SegmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/segments")
@Validated
public class SegmentsController {

    private final SegmentService segmentService;

    public SegmentsController(SegmentService segmentService) {

        this.segmentService = segmentService;
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public void saveBatch(@RequestBody @Valid SaveBatchRequest request) {
        var items = request.items().stream()
                .map(it -> new SegmentDTO(
                        it.startMs(), it.endMs(), it.score(), it.meta()))
                .toList();
        segmentService.saveBatch(request.mediaId(), items);
    }

    @GetMapping("/media/{mediaId}")
    public Page<Map<String,Object>> listByMedia(@PathVariable UUID mediaId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return segmentService.list(mediaId, PageRequest.of(page, size)).map(SegmentsController::toDto);
    }

    @GetMapping("/media/{mediaId}/top")
    public List<Map<String,Object>> topByScore(@PathVariable UUID mediaId,
                                    @RequestParam(defaultValue = "5") @Min(1) int limit) {
        return segmentService.topByScore(mediaId, limit).stream().map(SegmentsController::toDto).toList();
    }

    @DeleteMapping("/media/{mediaId}")
    public void deleteByMedia(@PathVariable UUID mediaId) {
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
}
