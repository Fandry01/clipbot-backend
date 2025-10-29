package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.DetectionParams;
import com.example.clipbot_backend.dto.FwVerboseResponse;
import com.example.clipbot_backend.dto.SegmentDTO;
import com.example.clipbot_backend.engine.Interfaces.DetectionEngine;
import com.example.clipbot_backend.model.Segment;
import com.example.clipbot_backend.model.Transcript;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.SegmentRepository;
import com.example.clipbot_backend.repository.TranscriptRepository;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@Service
public class DetectWorkflow {
    private static final Logger LOGGER = LoggerFactory.getLogger(DetectWorkflow.class);
    private final MediaRepository mediaRepo;
    private final TranscriptRepository transcriptRepo;
    private final SegmentRepository segmentRepo;
    private final StorageService storage;
    private final DetectionEngine detection;
    private final FasterWhisperClient fastWhisperClient;
    private final AudioWindowService audioWindowService;
    private final UrlDownloader urlDownloader;

    public DetectWorkflow(MediaRepository mediaRepo, TranscriptRepository transcriptRepo, SegmentRepository segmentRepo, StorageService storage, DetectionEngine detection, FasterWhisperClient fastWhisperClient, AudioWindowService audioWindowService, UrlDownloader urlDownloader) {
        this.mediaRepo = mediaRepo;
        this.transcriptRepo = transcriptRepo;
        this.segmentRepo = segmentRepo;
        this.storage = storage;
        this.detection = detection;
        this.fastWhisperClient = fastWhisperClient;
        this.audioWindowService = audioWindowService;
        this.urlDownloader = urlDownloader;
    }

    @Transactional
    public int run(UUID mediaId, Map<String,Object> payload) throws Exception {
        var media = mediaRepo.findById(mediaId).orElseThrow();

        String lang     = payload != null ? (String) payload.get("lang")     : null;
        String provider = payload != null ? (String) payload.get("provider") : null;

        Optional<Transcript> trOpt =
                (lang != null && provider != null)
                        ? transcriptRepo.findByMediaAndLangAndProvider(
                        media, lang.toLowerCase(Locale.ROOT), provider.toLowerCase(Locale.ROOT))
                        : transcriptRepo.findTopByMediaOrderByCreatedAtDesc(media);

        // === Nieuw: maak transcript indien ontbreekt ===
        Transcript tr = trOpt.orElseGet(() -> {
            Path input;
            String key = media.getObjectKey();

            if (key == null || key.isBlank()) {
                throw new IllegalStateException("media.objectKey invalid: " + key);
            }
            String src = media.getSource() == null ? "" : media.getSource().toLowerCase(Locale.ROOT);
            if ("url".equals(src)) {
                String external = Objects.requireNonNull(media.getExternalUrl(), "externalUrl is null for URL source");
                input = urlDownloader.ensureRawObject(external, key); // <--- download naar data/raw/<key>
            } else {
                input = storage.resolveRaw(key);
                if (!Files.exists(input)) {
                    throw new IllegalStateException("raw input missing: " + input);
                }
            }
            var fw = fastWhisperClient.transcribeFile(input, true);

            String detLang = (fw != null && fw.language() != null && !fw.language().isBlank())
                    ? fw.language().toLowerCase(Locale.ROOT)
                    : (lang == null ? "auto" : lang.toLowerCase(Locale.ROOT));

            String prov = (provider == null || provider.isBlank())
                    ? "fw" : provider.toLowerCase(Locale.ROOT);

            String text = (fw == null || fw.text() == null) ? "" : fw.text().strip();

            Transcript t = new Transcript(media, detLang, prov);
            t.setText(text);

            // Words JSON uit FW opbouwen (zelfde schema als in TranscriptService)
            List<Map<String, Object>> items = new ArrayList<>();
            if (fw != null) {
                if (fw.words() != null && !fw.words().isEmpty()) {
                    for (var w : fw.words()) {
                        Map<String,Object> m = new LinkedHashMap<>();
                        long s = w.start() == null ? 0L : Math.round(w.start() * 1000);
                        long e = w.end()   == null ? s  : Math.round(w.end()   * 1000);
                        m.put("startMs", s);
                        m.put("endMs",   e);
                        m.put("text",    w.word() == null ? "" : w.word().trim());
                        items.add(m);
                    }
                } else if (fw.segments() != null) {
                    for (var seg : fw.segments()) {
                        if (seg.words() == null) continue;
                        for (var w : seg.words()) {
                            Map<String,Object> m = new LinkedHashMap<>();
                            long s = w.start() == null ? 0L : Math.round(w.start() * 1000);
                            long e = w.end()   == null ? s  : Math.round(w.end()   * 1000);
                            m.put("startMs", s);
                            m.put("endMs",   e);
                            m.put("text",    w.word() == null ? "" : w.word().trim());
                            items.add(m);
                        }
                    }
                }
            }
            items.sort(Comparator.comparingLong(m -> (Long)m.get("startMs")));

            Map<String,Object> wordsDoc = new LinkedHashMap<>();
            wordsDoc.put("schema", "v1");
            wordsDoc.put("generatedAt", Instant.now().toString());
            wordsDoc.put("items", items);
            t.setWords(wordsDoc);

            return transcriptRepo.save(t);
        });

        // === Params
        var srcPath = storage.resolveRaw(media.getObjectKey());
        if (srcPath== null || !Files.exists(srcPath)) {
            throw new IllegalStateException("Media file missing: " + srcPath);
        }
        var params = DetectionParams.defaults().withMaxCandidates(8);

        Double sceneTh = parseDouble(payload, "sceneThreshold");
        if (sceneTh != null && sceneTh > 0) {
            params = new DetectionParams(
                    params.minDurationMs(), params.maxDurationMs(), params.maxCandidates(),
                    params.silenceNoiseDb(), params.silenceMinDurSec(), params.snapThresholdMs(),
                    params.targetLenSec(), params.lenSigmaSec(),
                    sceneTh, params.snapSceneMs(), params.sceneAlignBonus()
            );
        }

        // === Detect
        var detected = detection.detect(srcPath, tr, params);

        // === Refine
        long pad = 500;
        long MAX_SLICE_MS = 60_000;
        List<SegmentDTO> refined = new ArrayList<>(detected.size());

        for (var s : detected) {
            long start = s.startMs();
            long end   = Math.min(s.endMs(), start + MAX_SLICE_MS);

            var win = audioWindowService.sliceToTempWav(srcPath, start, end, pad);
            try {
                var fw = fastWhisperClient.transcribeFile(win.file(), true);
                var snapped = snapToWordBounds(s, fw, win.offsetMs());
                refined.add(snapped);
            } finally {
                try { Files.deleteIfExists(win.file()); } catch (Exception ignore) {}
            }
        }

        // === Opslaan (idempotent)
        segmentRepo.deleteByMedia(media); // <-- Zorg dat deze methode bestaat in je repo
        if (!refined.isEmpty()) {
            var toSave = new ArrayList<Segment>(refined.size());
            for (var s : refined) {
                var seg = new Segment(media, s.startMs(), s.endMs());
                seg.setScore(s.score());
                seg.setMeta(s.meta());
                toSave.add(seg);
            }
            segmentRepo.saveAll(toSave);
        }

        return refined.size();
    }


    private static Double parseDouble(Map<String,Object> m, String k) {
        if (m == null) return null;
        Object v = m.get(k);
        if (v instanceof Number n) return n.doubleValue();
        if (v != null) try { return Double.parseDouble(v.toString()); } catch (Exception ignore) {}
        return null;
    }

    private SegmentDTO snapToWordBounds(SegmentDTO seg, FwVerboseResponse fw, long offsetMs) {
        long s = seg.startMs(), e = seg.endMs();
        long bestS = s, bestE = e;

        if (fw != null && fw.segments() != null) {
            for (var fs : fw.segments()) {
                if (fs.words() == null) continue;
                for (var w : fs.words()) {
                    long ws = Math.round((w.start() == null ? 0.0 : w.start()) * 1000) + offsetMs;
                    long we = Math.round((w.end()   == null ? 0.0 : w.end())   * 1000) + offsetMs;
                    if (Math.abs(ws - s) < Math.abs(bestS - s)) bestS = ws;
                    if (Math.abs(we - e) < Math.abs(bestE - e)) bestE = we;
                }
            }
        }
        if (bestE <= bestS) { bestS = s; bestE = e; }

        Map<String,Object> meta = new LinkedHashMap<>(seg.meta()==null?Map.of():seg.meta());
        meta.put("refined", true);
        meta.put("offsetMs", offsetMs);
        return new SegmentDTO(bestS, bestE, seg.score(), meta);
    }

}
