package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.web.*;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Transcript;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.TranscriptRepository;
import com.example.clipbot_backend.service.TranscriptService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/transcripts")
public class TranscriptController {
    private final TranscriptService transcriptService;
    private final TranscriptRepository transcriptRepo;
    private final MediaRepository mediaRepo;

    public TranscriptController(TranscriptService transcriptService, TranscriptRepository transcriptRepo, MediaRepository mediaRepo) {
        this.transcriptService = transcriptService;
        this.transcriptRepo = transcriptRepo;
        this.mediaRepo = mediaRepo;
    }

    @GetMapping("/{id}")
    public TranscriptResponse getById(@PathVariable UUID id) {
        Transcript t = transcriptRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transcript not found: " + id));
        return toDto(t);
    }

    @GetMapping
    public TranscriptResponse getByKeys(@RequestParam UUID mediaId,
                                        @RequestParam String lang,
                                        @RequestParam String provider) {
        Media media = mediaRepo.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found: " + mediaId));
        Transcript t = transcriptRepo.findByMediaAndLangAndProvider(media,lang,"openai")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Transcript not found for media=" + mediaId + ", lang=" + lang + ", provider=" + provider));
        return toDto(t);
    }

    /** Handig voor UI: pak het nieuwste transcript voor een media. */
    @GetMapping("/by-media/{mediaId}")
    public List<TranscriptResponse> listByMedia(@PathVariable UUID mediaId) {
        Media media = mediaRepo.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found: " + mediaId));
        return transcriptRepo.findAllByMedia(media).stream().map(this::toDto).collect(Collectors.toList());
    }

    @PutMapping
    public UpsertResponse upsert(@Valid @RequestBody TranscriptUpsertRequest request){
        // validate media
        mediaRepo.findById(request.mediaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found: " + request.mediaId()));

        // words in elk gangbaar schema ondersteunen
        List<TranscriptionEngine.Word> words = extractWords(request.words());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schema", detectSchema(request.words()));
        metadata.put("source", "api-upsert"); // trace

        TranscriptionEngine.Result result = new TranscriptionEngine.Result(
                Optional.ofNullable(request.text()).orElse(""),
                words,
                request.lang(),
                request.provider(),
                metadata
        );
        UUID id = transcriptService.upsert(request.mediaId(), result);
        return new UpsertResponse(id);
    }

    // ---------- helpers ----------

    private TranscriptResponse toDto(Transcript t) {
        return new TranscriptResponse(
                t.getId(),
                t.getMedia().getId(),
                t.getLang(),
                t.getProvider(),
                Optional.ofNullable(t.getText()).orElse(""),
                Optional.ofNullable(t.getWords()).orElseGet(() -> Map.of("schema","v1","items", List.of())),
                Optional.ofNullable(t.getCreatedAt()).orElse(Instant.EPOCH),
                t.getVersion()
        );
    }

    @SuppressWarnings("unchecked")
    private static List<TranscriptionEngine.Word> extractWords(Object wordsRaw) {
        if (wordsRaw == null) return List.of();

        // 1) Als het al een lijst van woorden is: [{text,startMs,endMs}, ...]
        if (wordsRaw instanceof List<?> list) {
            return toWordListFromList(list);
        }

        // 2) Als het een map is: kijk naar keys 'items' | 'words' | 'segments'
        if (wordsRaw instanceof Map<?,?> mapRaw) {
            Map<String,Object> m = (Map<String,Object>) mapRaw;

            Object items = m.get("items");
            if (items instanceof List<?> l1) return toWordListFromList(l1);

            Object w = m.get("words");
            if (w instanceof List<?> l2) return toWordListFromList(l2);

            Object segments = m.get("segments");
            if (segments instanceof List<?> segs) {
                // { segments: [ { words:[...] }, ... ] }
                List<TranscriptionEngine.Word> out = new ArrayList<>();
                for (Object seg : segs) {
                    if (seg instanceof Map<?,?> segMap) {
                        Object inner = ((Map<String,Object>) segMap).get("words");
                        if (inner instanceof List<?> lw) out.addAll(toWordListFromList(lw));
                    }
                }
                return out;
            }
        }

        // fallback: geen herkenbaar schema
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<TranscriptionEngine.Word> toWordListFromList(List<?> rawList) {
        List<TranscriptionEngine.Word> out = new ArrayList<>(rawList.size());
        for (Object o : rawList) {
            if (!(o instanceof Map<?,?>)) continue;
            Map<String,Object> m = (Map<String,Object>) o;

            long startMs = numMs(m.get("startMs"), m.get("start"));
            long endMs   = numMs(m.get("endMs"),   m.get("end"));
            String text  = str(m.getOrDefault("text", m.getOrDefault("word","")));
            out.add(new TranscriptionEngine.Word(startMs, endMs, text));
        }
        return out;
    }

    private static long numMs(Object ms, Object sec) {
        if (ms instanceof Number n) return n.longValue();
        if (ms != null) {
            try { return Long.parseLong(String.valueOf(ms)); } catch (Exception ignore) {}
        }
        if (sec instanceof Number n) return Math.round(n.doubleValue() * 1000.0);
        if (sec != null) {
            try { return Math.round(Double.parseDouble(String.valueOf(sec)) * 1000.0); } catch (Exception ignore) {}
        }
        return 0L;
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }

    @SuppressWarnings("unchecked")
    private static String detectSchema(Object wordsRaw) {
        if (wordsRaw instanceof List<?>) return "list";
        if (wordsRaw instanceof Map<?,?> m) {
            if (((Map<String,Object>) m).containsKey("items")) return "map.items";
            if (((Map<String,Object>) m).containsKey("words")) return "map.words";
            if (((Map<String,Object>) m).containsKey("segments")) return "map.segments";
        }
        return "unknown";
    }
}
