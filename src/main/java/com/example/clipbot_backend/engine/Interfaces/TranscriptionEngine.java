package com.example.clipbot_backend.engine.Interfaces;

import com.example.clipbot_backend.dto.web.TranscriptionResult;

import java.nio.file.Path;

public interface TranscriptionEngine {
    TranscriptionResult transcribe(Path mediaFile, String langHint) throws Exception;
}
