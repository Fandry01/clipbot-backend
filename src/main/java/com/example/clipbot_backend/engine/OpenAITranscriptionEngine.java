package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.config.OpenAIAudioProperties;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;

import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OpenAITranscriptionEngine  implements TranscriptionEngine{
    private final StorageService storageService;
    private final WebClient client;
    private final OpenAIAudioProperties props;

    public OpenAITranscriptionEngine(StorageService storageService, @Qualifier("openAiWebClient")WebClient client, OpenAIAudioProperties props) {
        this.storageService = storageService;
        this.client = client;
        this.props = props;
    }
    @Override
    public Result transcribe(Request request) throws Exception {
        Path input = storageService.resolveRaw(request.objectKey());
        if (!Files.exists(input)) throw new IllegalArgumentException("input not found: " + input);

        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new FileSystemResource(input));
        form.add("model", props.getModel()); // bv. whisper-1 of gpt-4o-transcribe

        // Language hint: eerst request, anders global property
        String langHint = request.langHint() != null ? request.langHint() : props.getLanguage();
        if (langHint != null && !langHint.isBlank()) {
            form.add("language", langHint);
        }

        // 🔑 Zorg dat we rich JSON + word timestamps krijgen
        form.add("response_format", "verbose_json");
        // Whisper: ondersteunt granularities; 4o-transcribe kan dit negeren, is niet erg
        form.add("timestamp_granularities[]", "word");

        String json = client.post()
                .uri("/v1/audio/transcriptions")
                // laat Spring zelf de multipart boundary/type kiezen:
                // .contentType(MediaType.MULTIPART_FORM_DATA)  // ← weg laten
                .body(BodyInserters.fromMultipartData(form))
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(props.getTimeoutSeconds()));

        var root = new ObjectMapper().readTree(json);

        String text = optText(root, "text");
        String lang = optText(root, "language"); // kan ontbreken
        List<TranscriptionEngine.Word> words = new ArrayList<>();

        // words direct
        if (root.has("words") && root.get("words").isArray()) {
            for (var w : root.get("words")) words.add(parseWord(w));
        }
        // of per segment
        else if (root.has("segments") && root.get("segments").isArray()) {
            for (var seg : root.get("segments")) {
                if (seg.has("words")) {
                    for (var w : seg.get("words")) words.add(parseWord(w));
                }
                if (text == null && seg.has("text")) text = append(text, seg.get("text").asText());
                if (lang == null && seg.has("language")) lang = seg.get("language").asText();
            }
        }

        if (text == null) text = "";
        if (lang == null) lang = "auto";

        Map<String,Object> meta = Map.of(
                "provider", "openai",
                "model", props.getModel()
        );

        return new TranscriptionEngine.Result(text, words, lang, "openai", meta);
    }

    // helpers:
    private static String append(String base, String add) {
        return (base == null || base.isBlank()) ? add : (base + " " + add);
    }

    private Word parseWord(JsonNode w) {
        // Whisper verbose_json woorden bevatten meestal start/end (sec) + text
        long startMs = (long) Math.round(w.path("start").asDouble(0) * 1000);
        long endMs   = (long) Math.round(w.path("end").asDouble(0) * 1000);
        String text  = w.path("word").asText(w.path("text").asText(""));
        return new Word(startMs, endMs, text);
    }

    private String optText(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private static String firstNonBlank(JsonNode... nodes){
        for (var n : nodes) if (n!=null && !n.isMissingNode() && !n.isNull() && !n.asText().isBlank()) return n.asText();
        return null;
    }
    private static long asMillis(JsonNode msField, JsonNode secField) {
        if (msField != null && msField.isNumber()) return msField.asLong();
        if (secField != null && secField.isNumber()) return Math.round(secField.asDouble() * 1000.0);
        return 0L;
    }

}
