package com.example.clipbot_backend.engine.Interfaces;

import com.example.clipbot_backend.dto.web.TranscriptionResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface TranscriptionEngine {
    TranscriptionResult transcribe(Path mediaFile, String langHint) throws Exception;
    record Request(UUID mediaId, String objectKey, String langHint) {}
    record Word(long startMs, long endMs, String text) {}
    record Result(String text,
                  List<Word> words,
                  String lang,
                  String provider,
                  Map<String, Object> meta) {}

    Result transcribe(Request req) throws Exception;
}
