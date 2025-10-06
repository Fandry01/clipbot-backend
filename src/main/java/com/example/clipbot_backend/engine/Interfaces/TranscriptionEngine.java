package com.example.clipbot_backend.engine.Interfaces;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface TranscriptionEngine {
    record Request(UUID mediaId, String objectKey, String langHint) {}
    record Word(long startMs, long endMs, String text) {}
    record Result(String text,
                  List<Word> words,
                  String lang,
                  String provider,
                  Map<String, Object> meta) {}

    Result transcribe(Request req) throws Exception;
}
