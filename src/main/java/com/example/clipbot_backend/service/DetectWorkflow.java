package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.DetectionParams;
import com.example.clipbot_backend.dto.FwVerboseResponse;
import com.example.clipbot_backend.dto.SegmentDTO;
import com.example.clipbot_backend.dto.WordsParser;
import com.example.clipbot_backend.engine.Interfaces.DetectionEngine;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Segment;
import com.example.clipbot_backend.model.Transcript;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.SegmentRepository;
import com.example.clipbot_backend.repository.TranscriptRepository;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.example.clipbot_backend.util.MediaStatus;
import com.fasterxml.jackson.databind.JsonNode;
import io.netty.handler.timeout.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

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

    public int run(UUID mediaId, Map<String,Object> payload) throws Exception {
        LOGGER.debug("DW.run ENTER mediaId={} payloadKeys={}", mediaId, payload==null?0:payload.size());
        long t0 = System.nanoTime();
        // TX A: kort
        Media media = markProcessing(mediaId);
        LOGGER.debug("DW.run markProcessing ok status={} objectKey={}", media.getStatus(), media.getObjectKey());

        try {
            // --- No TX: IO / transcript ---
            String lang = optStr(payload, "lang");
            String provider = optStr(payload, "provider");
            LOGGER.debug("DW.run about to resolveOrCreateTranscript lang={} provider={}", lang, provider);

            Transcript tr = resolveOrCreateTranscript(media, lang, provider); // zonder TX
            LOGGER.info("DETECT words={} media={}",
                    (tr!=null && tr.getWords()!=null && tr.getWords().has("items")) ? tr.getWords().get("items").size() : -1,
                    media.getId());

            Path srcPath = requireRaw(media);
            DetectionParams params = buildParams(payload);
            var detected = detection.detect(srcPath, tr, params);

            var refined = refineWithWordBounds(srcPath, detected);

            // TX B: segments persist (REQUIRES_NEW)
            persistSegments(media.getId(), refined);

            // TX C: media status blijft PROCESSING (render zet READY)
            return refined.size();

        } catch (Exception e) {
            // TX C: failed (REQUIRES_NEW)
            markFailed(mediaId, e);
            throw e;
        }
    }
    @Transactional
    protected Media markProcessing(UUID mediaId) {
        var media = mediaRepo.findById(mediaId).orElseThrow();
        if (media.getStatus() != MediaStatus.PROCESSING) {
            media.setStatus(MediaStatus.PROCESSING);
            mediaRepo.saveAndFlush(media);
        }
        return media;
    }

    protected Transcript resolveOrCreateTranscript(Media media, String lang, String provider) throws Exception {
        LOGGER.debug("DW.resolveOrCreateTranscript media={} lang={} provider={}", media.getId(), lang, provider);

        // 1) Als er al een transcript is: return (laatste = ok voor detect)
        Transcript existing = transcriptRepo.findTopByMediaOrderByCreatedAtDesc(media).orElse(null);
        if (existing != null) {
            LOGGER.debug("DW transcript exists id={} createdAt={}", existing.getId(), existing.getCreatedAt());
            return existing;
        }

        // 2) RAW garanderen (download bij source=url)
        Path raw = requireRaw(media);
        long size = java.nio.file.Files.size(raw);
        LOGGER.info("FW START media={} key={} size={}B", media.getId(), media.getObjectKey(), size);

        // Provider-keuze (nu alleen fw; breid later uit met gpt engine)
        String chosen = (provider == null || provider.isBlank()) ? "fw" : provider.trim().toLowerCase(Locale.ROOT);
        if (!chosen.equals("fw")) {
            LOGGER.warn("Provider '{}' nog niet geimplementeerd in DetectWorkflow; fallback naar 'fw'", chosen);
            chosen = "fw";
        }

        long t0 = System.nanoTime();
        FwVerboseResponse fw;
        try {
            fw = fastWhisperClient.transcribeFile(raw, true); // word timestamps
        } catch (TimeoutException te) {
            LOGGER.warn("FW TIMEOUT media={} after {} ms", media.getId(), (System.nanoTime()-t0)/1_000_000);
            throw te;
        }
        long dtMs = (System.nanoTime()-t0)/1_000_000;
        int segCount = (fw != null && fw.segments()!=null) ? fw.segments().size() : -1;
        LOGGER.info("FW DONE  media={} in {} ms, segments={}", media.getId(), dtMs, segCount);

        // 4) Transcript entity vullen
        Transcript t = new Transcript();
        t.setMedia(media);
        t.setLang(  fw != null && fw.language()!=null ? fw.language().toLowerCase(Locale.ROOT) : defaultLang(lang));
        t.setProvider("fw");
        t.setText( fw != null && fw.text()!=null ? fw.text().strip() : "" );
        t.setWords( toWordsJson(fw) ); // JsonNode

        t = transcriptRepo.save(t);

        // Korte sanity-log (hoeveel woorden parsed door WordsParser)
        try {
            var words = WordsParser.extract(t);
            LOGGER.info("TRANSCRIPT SAVED media={} id={} words={}", media.getId(), t.getId(), words.size());
        } catch (Exception parseEx) {
            LOGGER.warn("WordsParser failed media={} id={} err={}", media.getId(), t.getId(), parseEx.toString());
        }

        return t;
    }





    private String defaultLang(String hint) {
        return hint == null || hint.isBlank() ? "auto" : hint.toLowerCase(Locale.ROOT);
    }

    private Path requireRaw(Media media) {
        String key = media.getObjectKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("media.objectKey invalid: " + key);
        }

        Path p = storage.resolveRaw(key);
        if (Files.exists(p)) return p;

        // Lazy download voor URL-bronnen
        String src = media.getSource() == null ? "" : media.getSource().toLowerCase(Locale.ROOT);
        if ("url".equals(src)) {
            if (media.getExternalUrl() == null || media.getExternalUrl().isBlank()) {
                throw new IllegalStateException("externalUrl is null for URL source");
            }
            // download precies onder de gekozen objectKey
            urlDownloader.ensureRawObject(media.getExternalUrl(), key);
            p = storage.resolveRaw(key);
            if (Files.exists(p)) return p;
        }

        throw new IllegalStateException("RAW missing: " + p);
    }



    protected DetectionParams buildParams(Map<String,Object> payload) {
        if (payload == null) return DetectionParams.defaults();

        long minMs   = asLong(payload.get("minDurationMs"),   10_000);
        long maxMs   = asLong(payload.get("maxDurationMs"),   60_000);
        int  maxCand = asInt (payload.get("maxCandidates"),   8);

        double silDb   = asDbl(payload.get("silenceNoiseDb"),   -35.0);
        double silMin  = asDbl(payload.get("silenceMinDurSec"), 0.5);
        long   snapMs  = asLong(payload.get("snapThresholdMs"), 400);

        double target  = asDbl(payload.get("targetLenSec"),   30.0);
        double sigma   = asDbl(payload.get("lenSigmaSec"),    10.0);

        double sceneT  = asDbl(payload.get("sceneThreshold"), 0.4);
        long   snapSc  = asLong(payload.get("snapSceneMs"),   400);
        double scBonus = asDbl(payload.get("sceneAlignBonus"),0.12);

        return new DetectionParams(
                minMs, maxMs, maxCand,
                silDb, silMin, snapMs,
                target, sigma,
                sceneT, snapSc, scBonus
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void persistSegments(UUID mediaId, List<SegmentDTO> refined) {
        var media = mediaRepo.getReferenceById(mediaId);
        int removed = segmentRepo.deleteByMedia(media);
        LOGGER.debug("Removed {} segments for media {}", removed, mediaId);
        //segmentRepo.deleteByMedia(media);
        if (!refined.isEmpty()) {
            var entities = new ArrayList<Segment>(refined.size());
            for (var s : refined) {
                var e = new Segment(media, s.startMs(), s.endMs());
                e.setScore(s.score() == null ? BigDecimal.ZERO : s.score());
                e.setMeta(s.meta() == null ? Map.of() : s.meta());
                entities.add(e);
            }
            segmentRepo.saveAll(entities);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void markFailed(UUID mediaId, Exception e) {
        var media = mediaRepo.findById(mediaId).orElseThrow();
        media.setStatus(MediaStatus.FAILED);
        mediaRepo.save(media);
        LOGGER.warn("Detect failed media={} err={}", mediaId, e.toString());
    }



    private static Double parseDouble(Map<String,Object> m, String k) {
        if (m == null) return null;
        Object v = m.get(k);
        if (v instanceof Number n) return n.doubleValue();
        if (v != null) try { return Double.parseDouble(v.toString()); } catch (Exception ignore) {}
        return null;
    }
    private JsonNode toWordsJson(FwVerboseResponse fw) {
        var f = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance;
        var root = f.objectNode();
        root.put("schema", "fw.verbose_json");
        var items = f.arrayNode();

        if (fw != null) {
            if (fw.words() != null) {
                for (var w : fw.words()) {
                    double sSec = (w.start() == null ? 0.0 : w.start());
                    double eSec = (w.end()   == null ? sSec : w.end());
                    long sMs = Math.max(0L, Math.round(sSec * 1000.0));
                    long eMs = Math.max(sMs, Math.round(eSec * 1000.0));

                    var n = f.objectNode();
                    n.put("text", w.word() == null ? "" : w.word());
                    n.put("startMs", sMs);
                    n.put("endMs",   eMs);
                    items.add(n);
                }
            } else if (fw.segments() != null) {
                for (var s : fw.segments()) {
                    if (s.words() == null) continue;
                    for (var w : s.words()) {
                        double sSec = (w.start() == null ? 0.0 : w.start());
                        double eSec = (w.end()   == null ? sSec : w.end());
                        long sMs = Math.max(0L, Math.round(sSec * 1000.0));
                        long eMs = Math.max(sMs, Math.round(eSec * 1000.0));

                        var n = f.objectNode();
                        n.put("text", w.word() == null ? "" : w.word());
                        n.put("startMs", sMs);
                        n.put("endMs",   eMs);
                        items.add(n);
                    }
                }
            }
        }

        root.set("items", items);
        return root;
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

    // in DetectWorkflow
    private List<SegmentDTO> refineWithWordBounds(Path srcPath, List<SegmentDTO> detected) {
        if (detected == null || detected.isEmpty()) return detected;

        final long PAD_MS = 300; // beetje context rond het segment
        List<SegmentDTO> out = new ArrayList<>(detected.size());

        for (SegmentDTO seg : detected) {
            AudioWindowService.Window win = null;
            try {
                // ✂️ maak tijdelijke WAV rond het segment
                win = audioWindowService.sliceToTempWav(srcPath, seg.startMs(), seg.endMs(), PAD_MS);

                // ⏱️ snelle woord-timestamps op het window
                FwVerboseResponse fw = fastWhisperClient.transcribeFile(win.file(), true);

                // 🎯 schuif grenzen naar dichtstbijzijnde woordranden
                SegmentDTO refined = snapToWordBounds(seg, fw, win.offsetMs());
                out.add(refined);
            } catch (Exception e) {
                // als refinement faalt, neem dan het originele segment
                out.add(seg);
            } finally {
                if (win != null) {
                    try { java.nio.file.Files.deleteIfExists(win.file()); } catch (Exception ignore) {}
                }
            }
        }
        return out;
    }

    private static long asLong(Object v, long def) {
        if (v instanceof Number n) return n.longValue();
        if (v != null) try { return Long.parseLong(v.toString()); } catch (Exception ignore) {}
        return def;
    }
    private static int asInt(Object v, int def) {
        if (v instanceof Number n) return n.intValue();
        if (v != null) try { return Integer.parseInt(v.toString()); } catch (Exception ignore) {}
        return def;
    }
    private static double asDbl(Object v, double def) {
        if (v instanceof Number n) return n.doubleValue();
        if (v != null) try { return Double.parseDouble(v.toString()); } catch (Exception ignore) {}
        return def;
    }
    private static String optStr(Map<String,Object> m, String k) {
        if (m == null) return null;
        Object v = m.get(k);
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s.toLowerCase(Locale.ROOT);
    }

}
