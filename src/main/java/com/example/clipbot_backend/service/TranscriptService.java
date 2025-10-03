package com.example.clipbot_backend.service;

import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Transcript;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.TranscriptRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class TranscriptService {
    private final TranscriptRepository transcriptRepo;
    private final MediaRepository mediaRepo;


    public TranscriptService(TranscriptRepository transcriptRepo, MediaRepository mediaRepo) {
        this.transcriptRepo = transcriptRepo;
        this.mediaRepo = mediaRepo;
    }


    @Transactional
    public UUID upsert(UUID mediaId, TranscriptionEngine.Result res) {
        Media media = mediaRepo.findById(mediaId).orElseThrow();

        Transcript transcript = transcriptRepo
                .findByMediaAndLangAndProvider(media, res.lang(), res.provider())
                .orElseGet(() -> new Transcript(media, res.lang(), res.provider()));

        transcript.setMedia(media);
        transcript.setLang(res.lang());
        transcript.setProvider(res.provider());
        transcript.setText(res.text() != null ? res.text() : "");

        // Words -> JSON document
        List<Map<String, Object>> items = (res.words() == null) ? List.of()
                : res.words().stream().map(w -> Map.<String,Object>of(
                "startMs", w.startMs(),
                "endMs",   w.endMs(),
                "text",    w.text()
        )).toList();

        Map<String, Object> wordsDoc = Map.of(
                "schema", "v1",
                "generatedAt", java.time.Instant.now().toString(),
                "items", items
        );
        transcript.setWords(wordsDoc);

        transcriptRepo.save(transcript);
        return transcript.getId();
    }



    public Optional<Transcript> get(UUID mediaId, String lang, String provider){
        Media media = mediaRepo.findById(mediaId).orElseThrow();
        return transcriptRepo.findByMediaAndLangAndProvider(media, lang, provider);
    }
}
