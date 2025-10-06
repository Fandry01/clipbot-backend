package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.dto.*;
import com.example.clipbot_backend.engine.Interfaces.DetectionEngine;
import com.example.clipbot_backend.model.Transcript;
import com.example.clipbot_backend.service.ClipAssembler;
import com.example.clipbot_backend.service.SilenceDetector;
import com.example.clipbot_backend.util.TranscriptUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DetectionEngineImpl implements DetectionEngine {
    private final SilenceDetector silenceDetector;
    private final ClipAssembler assembler = new ClipAssembler();

    public DetectionEngine(SilenceDetector silenceDetector){
        this.silenceDetector = silenceDetector;
    }

    @Override
    List<SegmentDTO> detect (Path mediaFile, Transcript transcript, DetectionParams params){
        //1) woorden uit jouw JSON
        List<WordsParser.WordAdapter> words = WordsParser.extract(transcript);

        // 2) Zinnen bouwen (generieke Splitter)
        List<SentenceSpan> sentences = TranscriptUtil.toSentences(
                words,
                w -> w.text,
                w -> w.startMs,
                w -> w.endMs
        );
        // 3 Stiltes Ophalen
        List<SilenceEvent> silences = silenceDetector.detect(
                mediaFile, params.silenceNoiseDb, params.silenceMinDurSec
        );

        //4 windows gnereren + scoren
        var wins = assembler.windows(
                sentences, silences,
                params.minDurationMs, params.maxDurationMs, params.snapThresholdMs,
                params.targetLenSec, params.lenSigmaSec,
                params.maxCandidates
        );

        // 5) map naar SegmentDTO (bigDecimal score + meta met componenten)

        return wins.stream().map(w ->{
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.putAll(w.scoreComponents); // overal, lenscore, hasHook, hasPayoff,boundaryBonus
                    meta.put("startIdx", w.startIdx);
                    meta.put("endidx", w.endIdx);
                    meta.put("snapped", true);
                    meta.put("snapThresholdMs", params.snapThresholdMs);
                    meta.put("minDurationMs", params.minDurationMs);
                    meta.put("maxDurationMs", params.maxDurationMs);
                    BigDecimal score = BigDecimal.valueOf(w.score).setScale(4, RoundingMode.HALF_UP);
                    return new SegmentDTO(w.startMs, w.endMs, score, meta);
                }
        ).collect(Collectors.toList());
    }
}
