package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.WordsParser;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Transcript;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.TranscriptRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class TranscriptService {
    private final TranscriptRepository transcriptRepo;
    private final MediaRepository mediaRepo;
    private final ObjectMapper om;
    private static final Logger LOGGER = LoggerFactory.getLogger(TranscriptService.class);

    public TranscriptService(TranscriptRepository transcriptRepo, MediaRepository mediaRepo, ObjectMapper om) {
        this.transcriptRepo = transcriptRepo;
        this.mediaRepo = mediaRepo;
        this.om = om;
    }
    private static String nlw(String s, String def) {
        return (s == null || s.isBlank()) ? def : s.toLowerCase(Locale.ROOT);
    }
    /** Idempotentie: is er *enig* transcript (ongeacht lang/provider) voor dit mediaId? */
    @Transactional()
    public boolean existsAnyFor(UUID mediaId) {
        // getReferenceById vermijdt DB-hit voor het object zelf (proxy), en laat JPA de exists-query doen
        Media mediaRef = mediaRepo.getReferenceById(mediaId);
        return transcriptRepo.existsByMedia(mediaRef);
    }


    @Transactional
    public UUID upsert(UUID mediaId, TranscriptionEngine.Result res) {
        Media media = mediaRepo.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));

        String lang     = nlw(res.lang(), "auto");
        String provider = nlw(res.provider(), "unknown");

        Transcript transcript = transcriptRepo
                .findByMediaAndLangAndProvider(media,lang,provider)
                .orElseGet(() -> new Transcript(media, lang, provider));

        transcript.setMedia(media);
        transcript.setLang(lang);
        transcript.setProvider(provider);
        transcript.setText(res.text() != null ? res.text() : "");

        // Words -> JSON document
        List<Map<String, Object>> items = (res.words() == null) ? List.of()
                : res.words().stream().map(w -> {
                    long s = Math.max(0, w.startMs());
                    long e = Math.max(s, w.endMs());
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("startMs", s);
            m.put("endMs",   e);
            m.put("text",    w.text());
            return m;
        }).sorted(Comparator.comparingLong(m -> (Long)m.get("startMs")))
                .toList();

        // >>> Gebruik een MUTABLE map i.p.v. Map.of(...)
        Map<String, Object> wordsDoc = new LinkedHashMap<>();
        wordsDoc.put("schema", "v1");
        wordsDoc.put("generatedAt", java.time.Instant.now().toString());
        wordsDoc.put("lang", lang);
        wordsDoc.put("provider", provider);
        wordsDoc.put("items", items);

        // >>> Meta uit Result heet 'meta', niet 'metadata'
        Object segsObj = (res.meta() == null) ? null : res.meta().get("segments");
        if (segsObj instanceof List<?> segs) {
            wordsDoc.put("segments", segs);
            wordsDoc.put("diarizeProvider", res.provider());
        }
        JsonNode wordsNode = om.valueToTree(wordsDoc);
        transcript.setWords(wordsNode);
        try {
            var saved = transcriptRepo.saveAndFlush(transcript); // forceer persist + id
            var words = WordsParser.extract(saved);
            LOGGER.info("Transcript upsert media={} lang={} provider={} words={}",
                    mediaId, lang, provider, words.size());
            return saved.getId();
        } catch (DataIntegrityViolationException dup) {
            // unique (media,lang,provider) – race safe
            return transcriptRepo.findByMediaAndLangAndProvider(media, lang, provider)
                    .orElseThrow().getId();
        }
    }



    @Transactional
    public Optional<Transcript> get(UUID mediaId, String lang, String provider){
        Media media = mediaRepo.findById(mediaId).orElseThrow();
        String l = (lang == null || lang.isBlank()) ? null : lang.toLowerCase(Locale.ROOT);
        String p = (provider == null || provider.isBlank()) ? null : provider.toLowerCase(Locale.ROOT);
        if (l != null && p != null) {
            return transcriptRepo.findByMediaAndLangAndProvider(media, l, p);
        }
        return transcriptRepo.findTopByMediaOrderByCreatedAtDesc(media);
    }
}
