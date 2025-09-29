package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.TranscriptionResult;
import com.example.clipbot_backend.engine.ITranscriptionEngine;
import com.example.clipbot_backend.engine.TranscriptionEngine;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Map;

@Service
public class DummyTranscriptionEngine implements ITranscriptionEngine {

    public TranscriptionResult transcribe(Path mediaFile, String langHint) {
        return new TranscriptionResult("en", "whisper",
                "Hello world transcript ...", Map.of("words", java.util.List.of()));
    }
}

