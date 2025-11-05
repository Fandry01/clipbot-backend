package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.dto.DetectionParams;
import com.example.clipbot_backend.dto.SegmentDTO;
import com.example.clipbot_backend.dto.SentenceSpan;
import com.example.clipbot_backend.dto.SilenceEvent;
import com.example.clipbot_backend.dto.WordsParser;
import com.example.clipbot_backend.engine.Interfaces.DetectionEngine;
import com.example.clipbot_backend.model.Transcript;
import com.example.clipbot_backend.service.ClipAssembler;
import com.example.clipbot_backend.service.Interfaces.SilenceDetector;
import com.example.clipbot_backend.util.TranscriptUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class DetectionEngineImpl implements DetectionEngine {
    private final SilenceDetector silenceDetector;
    private final ClipAssembler assembler = new ClipAssembler();

    public DetectionEngineImpl(SilenceDetector silenceDetector) {
        this.silenceDetector = silenceDetector;
    }

    @Override
    public List<SegmentDTO> detect(Path mediaFile, Transcript transcript, DetectionParams params) {
        if (transcript == null) return List.of();
        List<WordsParser.WordAdapter> words = WordsParser.extract(transcript);
        if (words.isEmpty()) return List.of();
        if (mediaFile == null || !Files.exists(mediaFile))
            throw new IllegalArgumentException("Media file missing: " + mediaFile);

        List<SentenceSpan> sentences = TranscriptUtil.toSentences(
                words, w -> w.text, w -> w.startMs, w -> w.endMs
        );

        List<SilenceEvent> silences;
        try {
            silences = Optional.ofNullable(
                    silenceDetector.detect(mediaFile, params.silenceNoiseDb(), params.silenceMinDurSec())
            ).orElse(List.of());
        } catch (Exception e) {
            silences = List.of();
        }

        var wins = assembler.windows(
                sentences, silences,
                params.minDurationMs(), params.maxDurationMs(), params.snapThresholdMs(),
                params.targetLenSec(), params.lenSigmaSec(),
                params.maxCandidates()
        );

        long mediaEndGuess = words.get(words.size()-1).endMs;

        return wins.stream().map(w -> {
            Map<String,Object> meta = new LinkedHashMap<>();
            if (w.scoreComponents != null) meta.putAll(w.scoreComponents);
            meta.put("startIdx", w.startIdx);
            meta.put("endIdx", w.endIdx);
            meta.put("snapped", true);
            meta.put("snapThresholdMs", params.snapThresholdMs());
            meta.put("minDurationMs", params.minDurationMs());
            meta.put("maxDurationMs", params.maxDurationMs());
            meta.put("schema","det-v1");
            meta.put("engine","clip-assembler");

            long s = Math.max(0, w.startMs);
            long e = Math.min(Math.max(s + 1, w.endMs), mediaEndGuess);

            BigDecimal score = BigDecimal.valueOf(w.score).setScale(4, RoundingMode.HALF_UP);
            return new SegmentDTO(s, e, score, meta);
        }).toList();
    }

}
