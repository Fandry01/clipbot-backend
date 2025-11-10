package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.web.*;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Transcript;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.TranscriptRepository;
import com.example.clipbot_backend.service.TranscriptService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
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
    private final ObjectMapper om; //

    public TranscriptController(TranscriptService transcriptService, TranscriptRepository transcriptRepo, MediaRepository mediaRepo, ObjectMapper om) {
        this.transcriptService = transcriptService;
        this.transcriptRepo = transcriptRepo;
        this.mediaRepo = mediaRepo;
        this.om = om;
    }
    private ObjectNode emptyWords() {
        ObjectNode o = om.createObjectNode();
        o.put("schema", "v1");
        o.putArray("items"); // lege array
        return o;
    }

    @GetMapping("/{id}")
    public TranscriptResponse getById(@PathVariable UUID id,@RequestParam String ownerExternalSubject) {
        Transcript t = transcriptRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transcript not found: " + id));
        ensureOwnedBy(t.getMedia(), ownerExternalSubject);
        return toDto(t);
    }

    @GetMapping
    public TranscriptResponse getByKeys(@RequestParam UUID mediaId,
                                        @RequestParam String lang,
                                        @RequestParam String provider,
                                        @RequestParam String ownerExternalSubject) {
        Media media = mediaRepo.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found: " + mediaId));
        ensureOwnedBy(media, ownerExternalSubject);
        Transcript t = transcriptRepo.findByMediaAndLangAndProvider(media,lang,provider)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Transcript not found for media=" + mediaId + ", lang=" + lang + ", provider=" + provider));
        return toDto(t);
    }

    /** Handig voor UI: pak het nieuwste transcript voor een media. */
    @GetMapping("/by-media/{mediaId}")
    public List<TranscriptResponse> listByMedia(@PathVariable UUID mediaId, @RequestParam String ownerExternalSubject, @RequestParam(required = false, defaultValue = "10") int limit) {
        Media media = mediaRepo.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found: " + mediaId));
        ensureOwnedBy(media, ownerExternalSubject);
        return transcriptRepo.findTopByMediaOrderByCreatedAtDesc(media)
                .stream().map(this::toDto).toList();
    }

    @PutMapping
    public UpsertResponse upsert(@Valid @RequestBody TranscriptUpsertRequest request){
        // validate media
        mediaRepo.findById(request.mediaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found: " + request.mediaId()));

        // words in elk gangbaar schema ondersteunen
        List<TranscriptionEngine.Word> words = extractWords(request.words()).stream().map(w -> new TranscriptionEngine.Word(
                        Math.max(0L, w.startMs()),
                        Math.max(Math.max(0L, w.startMs()), w.endMs()),
                        w.text() == null ? "" : w.text()))
                .sorted(Comparator.comparingLong(TranscriptionEngine.Word::startMs)).toList();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schema", detectSchema(request.words()));
        metadata.put("source", "api-upsert"); // trace

        String lang = request.lang() == null ? "auto" : request.lang().toLowerCase(Locale.ROOT);
        String provider = request.provider() == null ? "unknown" : request.provider().toLowerCase(Locale.ROOT);

        TranscriptionEngine.Result result = new TranscriptionEngine.Result(
                Optional.ofNullable(request.text()).orElse(""),
                words,
                lang,
                provider,
                metadata
        );
        UUID id = transcriptService.upsert(request.mediaId(), result);
        return new UpsertResponse(id);
    }

    // ---------- helpers ----------

    private TranscriptResponse toDto(Transcript t) {
        JsonNode words = (t.getWords() != null) ? t.getWords() : emptyWords();
        return new TranscriptResponse(
                t.getId(),
                t.getMedia().getId(),
                t.getLang(),
                t.getProvider(),
                Optional.ofNullable(t.getText()).orElse(""),
                words,
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

    private static ObjectNode emptyWordsStatic() {
        var f = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance;
        var o = f.objectNode();
        o.put("schema", "v1");
        o.set("items", f.arrayNode());
        return o;
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
        if (wordsRaw instanceof Map<?, ?> mapAny) {
            Map<String, Object> m = (Map<String, Object>) mapAny;
            if (m.containsKey("items"))    return "map.items";
            if (m.containsKey("words"))    return "map.words";
            if (m.containsKey("segments")) return "map.segments";
        }
        return "unknown";
    }

    private void ensureOwnedBy(Media media, String subject) {
        String ownerSub = media.getOwner().getExternalSubject();
        if (!Objects.equals(ownerSub, subject))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "MEDIA_NOT_OWNED");
    }
}
