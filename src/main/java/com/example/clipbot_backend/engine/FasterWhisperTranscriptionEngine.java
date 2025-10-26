package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.dto.FwVerboseResponse;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.service.FasterWhisperClient;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

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
        if (resp != null) {
            if (resp.words() != null && !resp.words().isEmpty()) {
                for (var w : resp.words()) {
                    long s = w.start() == null ? 0L : Math.round(w.start() * 1000);
                    long e = w.end()   == null ? s  : Math.round(w.end()   * 1000);
                    words.add(new Word(s, e, safeTrim(w.word())));
                }
            } else if (resp.segments() != null) {
                for (var seg : resp.segments()) {
                    if (seg.words() == null) continue;
                    for (var w : seg.words()) {
                        long s = w.start() == null ? 0L : Math.round(w.start() * 1000);
                        long e = w.end()   == null ? s  : Math.round(w.end()   * 1000);
                        words.add(new Word(s, e, safeTrim(w.word())));
                    }
                }
            }
        }
        words.sort(Comparator.comparingLong(Word::startMs));

        String lang = (resp != null && resp.language() != null && !resp.language().isBlank())
                ? resp.language().toLowerCase(Locale.ROOT) :
                (req.langHint() == null ? "auto" : req.langHint().toLowerCase(Locale.ROOT));

        String text = resp == null || resp.text() == null ? "" : resp.text().strip();

        return new Result(text, words, lang, "FW",
                Map.of("source","faster-whisper","schema","verbose_json"));
    }
    private static String safeTrim(String s){ return s==null ? "" : s.trim(); }
}
