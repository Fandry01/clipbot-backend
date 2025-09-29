package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.dto.DetectionParams;
import com.example.clipbot_backend.dto.SegmentDTO;
import com.example.clipbot_backend.model.Transcript;

import java.nio.file.Path;
import java.util.List;

public interface IDetectionEngine {
    List<SegmentDTO> detect(Path mediaFile, Transcript transcript, DetectionParams params) throws Exception;
}
