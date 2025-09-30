package com.example.clipbot_backend.engine.Interfaces;

import com.example.clipbot_backend.dto.web.DetectionParams;
import com.example.clipbot_backend.dto.SegmentDTO;
import com.example.clipbot_backend.model.Transcript;

import java.nio.file.Path;
import java.util.List;

public interface DetectionEngine {
    List<SegmentDTO> detect(Path mediaFile, Transcript transcript, DetectionParams params) throws Exception;
}
