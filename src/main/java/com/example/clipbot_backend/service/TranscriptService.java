package com.example.clipbot_backend.service;

import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Transcript;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.TranscriptRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

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
    public UUID upsert(UUID mediaId, String lang, String provider, String text, Map<String, Object> words){
        Media media = mediaRepo.findById(mediaId).orElseThrow();
        Transcript transcript = transcriptRepo.findByMediaAndLangAndProvider(media, lang, provider). orElseGet(()-> new Transcript(media, lang,provider));
        transcript.setText(text);
        transcript.setWords(words);
        transcriptRepo.save(transcript);
        return transcript.getId();
    }
    public Optional<Transcript> get(UUID mediaId, String lang, String provider){
        Media media = mediaRepo.findById(mediaId).orElseThrow();
        return transcriptRepo.findByMediaAndLangAndProvider(media, lang, provider);
    }
}
