package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.SilenceEvent;

import java.nio.file.Path;
import java.util.List;

public interface SilenceDetector {
    List<SilenceEvent> detect(Path mediaPath, double noiseDb, double minSilenceSec);
}
