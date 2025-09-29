package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.dto.TranscriptionResult;

import java.nio.file.Path;

public interface ITranscriptionEngine {
    TranscriptionResult transcribe(Path mediaFile, String langHint) throws Exception;
}
