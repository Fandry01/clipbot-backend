package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.dto.web.DetectionParams;
import com.example.clipbot_backend.dto.SegmentDTO;
import com.example.clipbot_backend.engine.Interfaces.DetectionEngine;
import com.example.clipbot_backend.model.Transcript;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Map;

@Service
class DummyDetectionEngine implements DetectionEngine {
    public java.util.List<SegmentDTO> detect(Path mediaFile, Transcript t, DetectionParams p) {
        return java.util.List.of(
                new SegmentDTO(0, 15000, new java.math.BigDecimal("0.900"), Map.of("rule","intro")),
                new SegmentDTO(30000, 45000, new java.math.BigDecimal("0.850"), Map.of("rule","punchline"))
        );
    }
}
