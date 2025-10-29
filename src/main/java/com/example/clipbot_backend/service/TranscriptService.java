package com.example.clipbot_backend.service;

import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Transcript;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.TranscriptRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TranscriptService {
    private final TranscriptRepository transcriptRepo;
    private final MediaRepository mediaRepo;


    public TranscriptService(TranscriptRepository transcriptRepo, MediaRepository mediaRepo) {
        this.transcriptRepo = transcriptRepo;
        this.mediaRepo = mediaRepo;
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
        Media media = mediaRepo.findById(mediaId).orElseThrow();

        String lang     = nlw(res.lang(), "auto");
        String provider = nlw(res.provider(), "unknown");

        Transcript transcript = transcriptRepo
                .findByMediaAndLangAndProvider(media,lang,provider)
                .orElseGet(() -> new Transcript(media, res.lang(), res.provider()));

        transcript.setMedia(media);
        transcript.setLang(res.lang() != null ? res.lang() : "auto");
        transcript.setProvider(res.provider() != null ? res.provider() : "unknown");
        transcript.setText(res.text() != null ? res.text() : "");

        // Words -> JSON document
        List<Map<String, Object>> items = (res.words() == null) ? List.of()
                : res.words().stream().map(w -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("startMs", w.startMs());
            m.put("endMs",   w.endMs());
            m.put("text",    w.text());
            return m;
        }).toList();

        // >>> Gebruik een MUTABLE map i.p.v. Map.of(...)
        Map<String, Object> wordsDoc = new LinkedHashMap<>();
        wordsDoc.put("schema", "v1");
        wordsDoc.put("generatedAt", java.time.Instant.now().toString());
        wordsDoc.put("items", items);

        // >>> Meta uit Result heet 'meta', niet 'metadata'
        Object segsObj = (res.meta() == null) ? null : res.meta().get("segments");
        if (segsObj instanceof List<?> segs) {
            wordsDoc.put("segments", segs);
            wordsDoc.put("diarizeProvider", res.provider());
        }

        transcript.setWords(wordsDoc);

        transcriptRepo.save(transcript);
        return transcript.getId();
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
