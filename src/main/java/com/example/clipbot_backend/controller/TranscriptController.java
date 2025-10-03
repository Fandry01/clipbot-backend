package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.web.*;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Transcript;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.TranscriptRepository;
import com.example.clipbot_backend.service.TranscriptService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
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
                .orElseThrow(() -> new RuntimeException("Transcript not found: " + id));
        return toDto(t);
    }

    @GetMapping
    public TranscriptResponse getByKeys(@RequestParam UUID mediaId,
                                        @RequestParam String lang,
                                        @RequestParam String provider) {
        Media media = mediaRepo.findById(mediaId)
                .orElseThrow(() -> new RuntimeException("Media not found: " + mediaId));
        Transcript t = transcriptRepo.findByMediaAndLangAndProvider(media, lang, provider)
                .orElseThrow(() -> new RuntimeException("Transcript not found for media=" + mediaId + ", lang=" + lang + ", provider=" + provider));
        return toDto(t);
    }

    @GetMapping("/by-media/{mediaId}")
    public List<TranscriptResponse> listByMedia(@PathVariable UUID mediaId) {
        Media media = mediaRepo.findById(mediaId)
                .orElseThrow(() -> new RuntimeException("Media not found: " + mediaId));
        return transcriptRepo.findAllByMedia(media).stream().map(this::toDto).collect(Collectors.toList());
    }

    @PutMapping
    public UpsertResponse upsert(@Valid @RequestBody TranscriptUpsertRequest request){
        List<TranscriptionEngine.Word> words = Optional.ofNullable(request.words())
                .map(w -> (List<Map<String,Object>>) w.getOrDefault("items", List.of()))
                .orElse(List.of())
                .stream()
                .map(it -> new TranscriptionEngine.Word(
                        ((Number) it.getOrDefault("startMs",0)).longValue(),
                        ((Number) it.getOrDefault("endMs",0)).longValue(),
                        String.valueOf(it.getOrDefault("text", "")))).toList();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("Schema", Optional.ofNullable(request.words()).map(m -> m.getOrDefault("schema","v1")).orElse("v1"));

        TranscriptionEngine.Result result = new TranscriptionEngine.Result( Optional.ofNullable(request.text()).orElse(""),
                words,
                request.lang(),
                request.provider(),
                metadata);
        UUID id = transcriptService.upsert(request.mediaId(), result);
        return new UpsertResponse(id);
    }

    private TranscriptResponse toDto(Transcript t) {
        return new TranscriptResponse(
                t.getId(),
                t.getMedia().getId(),
                t.getLang(),
                t.getProvider(),
                Optional.ofNullable(t.getText()).orElse(""),
                Optional.ofNullable(t.getWords()).orElse(Map.of("schema","v1","items", List.of())),
                t.getCreatedAt(),
                t.getVersion()
        );
    }

}
