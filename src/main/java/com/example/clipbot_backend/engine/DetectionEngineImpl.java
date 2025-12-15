package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.dto.DetectionParams;
import com.example.clipbot_backend.dto.SegmentDTO;
import com.example.clipbot_backend.dto.SentenceSpan;
import com.example.clipbot_backend.dto.SilenceEvent;
import com.example.clipbot_backend.dto.SpeakerTurn;
import com.example.clipbot_backend.dto.WordsParser;
import com.example.clipbot_backend.engine.Interfaces.DetectionEngine;
import com.example.clipbot_backend.model.Transcript;
import com.example.clipbot_backend.service.ClipAssembler;
import com.example.clipbot_backend.service.Interfaces.SilenceDetector;
import com.example.clipbot_backend.service.WorkerService;
import com.example.clipbot_backend.util.HeuristicScorer;
import com.example.clipbot_backend.util.TranscriptUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(DetectionEngineImpl.class);

    public DetectionEngineImpl(SilenceDetector silenceDetector) {
        this.silenceDetector = silenceDetector;
    }

    @Override
    public List<SegmentDTO> detect(Path mediaFile, Transcript transcript, DetectionParams params) {
        if (transcript == null) return List.of();
        List<WordsParser.WordAdapter> words = WordsParser.extract(transcript);
        List<SpeakerTurn> speakerTurns = WordsParser.extractSpeakerTurns(transcript);
        HeuristicScorer.SpeakerContext speakerContext = new HeuristicScorer.SpeakerContext(speakerTurns, params.speakerTurnsEnabled());
        LOGGER.info("DETECT extracted words={}, mediaFileExists={}", words.size(), Files.exists(mediaFile));
        LOGGER.debug("DETECT diarization segments={} speakerHeuristicsEnabled={}", speakerTurns.size(), params.speakerTurnsEnabled());

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
                params.maxCandidates(),
                speakerContext
        );
        if (wins.isEmpty()) {
            wins = assembler.windowsTextOnly(
                    sentences,
                    params.minDurationMs(),        // minMs (long)
                    params.maxDurationMs(),        // maxMs (long)
                    params.targetLenSec(),         // double
                    params.lenSigmaSec(),          // double
                    Math.max(params.maxCandidates(), 12), // int
                    speakerContext
            );
            LOGGER.info("DETECT fallback text-only windows={}", wins.size());
        }

        long mediaEndGuess = words.get(words.size()-1).endMs;
        LOGGER.info("DETECT sentences={}, silences={}, windows={}", sentences.size(), silences.size(), wins.size());

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
