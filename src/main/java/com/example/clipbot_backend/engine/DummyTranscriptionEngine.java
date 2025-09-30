package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.dto.web.TranscriptionResult;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Map;

@Service
public class DummyTranscriptionEngine implements TranscriptionEngine {

    public TranscriptionResult transcribe(Path mediaFile, String langHint) {
        return new TranscriptionResult("en", "whisper",
                "Hello world transcript ...", Map.of("words", java.util.List.of()));
    }
}

