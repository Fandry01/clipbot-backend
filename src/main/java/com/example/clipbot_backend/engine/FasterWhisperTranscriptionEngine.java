package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.dto.FwVerboseResponse;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.service.FasterWhisperClient;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Qualifier("fasterWhisperEngine")
public class FasterWhisperTranscriptionEngine implements TranscriptionEngine {
    private final StorageService storage;
    private final FasterWhisperClient client;


    public FasterWhisperTranscriptionEngine(StorageService storage, FasterWhisperClient client) {
        this.storage = storage;
        this.client = client;
    }

    @Override
    public Result transcribe(Request req) {
        Path input = storage.resolveRaw(req.objectKey());
        if (!Files.exists(input)) throw new IllegalArgumentException("input not found: " + input);

        FwVerboseResponse resp = client.transcribeFile(input, true);

        List<Word> words = new ArrayList<>();
        if (resp != null && resp.segments() != null) {
            for (var s : resp.segments()) {
                if (s.words() == null) continue;
                for (var w : s.words()) {
                    long startMs = w.start() == null ? 0L : Math.round(w.start() * 1000);
                    long endMs   = w.end()   == null ? startMs : Math.round(w.end() * 1000);
                    words.add(new Word(startMs, endMs, w.word()));
                }
            }
        }
        String text = resp == null || resp.text() == null ? "" : resp.text();

        return new Result(text, words,
                req.langHint() == null ? "auto" : req.langHint(),
                "FW",
                Map.of("source","faster-whisper","schema","verbose_json"));
    }
}
