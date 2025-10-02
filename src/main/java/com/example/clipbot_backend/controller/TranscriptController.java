package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.web.TranscriptResponse;
import com.example.clipbot_backend.dto.web.TranscriptUpsertRequest;
import com.example.clipbot_backend.dto.web.TranscriptUpsertResponse;
import com.example.clipbot_backend.service.TranscriptService;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/transcripts")
public class TranscriptController {
    private final TranscriptService transcriptService;

    public TranscriptController(TranscriptService transcriptService) {
        this.transcriptService = transcriptService;
    }

    @PutMapping
    public TranscriptUpsertResponse upsert(@RequestBody TranscriptUpsertRequest request){
        UUID id = transcriptService.upsert(request.mediaId(), request.lang(), request.provider(), request.text(), request.words());
        return new TranscriptUpsertResponse(id);
    }

    @GetMapping
    public Optional<TranscriptResponse> get(@RequestParam UUID mediaId, @RequestParam String lang, @RequestParam String provider){
        return transcriptService.get(mediaId, lang,provider).map( transcript -> new TranscriptResponse(transcript.getId(), transcript.getMedia().getId(), transcript.getLang(), transcript.getProvider(), transcript.getText()));
    }

}
