package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.config.OpenAIAudioProperties;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OpenAITranscriptionEngine implements TranscriptionEngine {
    private final StorageService storageService;
    private final WebClient client;
    private final OpenAIAudioProperties props;

    public OpenAITranscriptionEngine(StorageService storageService, WebClient client, OpenAIAudioProperties props) {
        this.storageService = storageService;
        this.client = client;
        this.props = props;
    }
    @Override
    public Result transcribe(Request request) throws Exception{
        Path input = storageService.resolveRaw(request.objectKey());
        if(!Files.exists(input)) throw new IllegalArgumentException("input not found: " + input);
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new FileSystemResource(input));
        form.add("model", props.getModel());
        if (request.langHint() != null) form.add("language", request.langHint());
        else if (props.getLanguage() != null) form.add("language", props.getLanguage());
        String json = client.post()
                .uri("/v1/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(form))
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(props.getTimeoutSeconds()));
        var root = new ObjectMapper().readTree(json);
        String text = optText(root, "text");
        String lang = optText(root, "language"); // kan ontbreken
        List<Word> words = new ArrayList<>();

        // words (vlak) of per segment
        if (root.has("words") && root.get("words").isArray()) {
            for (var w : root.get("words")) words.add(parseWord(w));
        } else if (root.has("segments") && root.get("segments").isArray()) {
            for (var seg : root.get("segments")) {
                if (seg.has("words"))
                    for (var w : seg.get("words")) words.add(parseWord(w));
                if (text == null && seg.has("text")) text = append(text, seg.get("text").asText());
                if (lang == null && seg.has("language")) lang = seg.get("language").asText();
            }
        }
        if (text == null) text = "";
        Map<String,Object> meta = Map.of(
                "provider", "openai",
                "model", props.getModel()
        );
        return new Result(text, words, lang != null ? lang : "auto", "openai", meta);

    }
    private static TranscriptionEngine.Word parseWord(JsonNode w) {
        long startMs = asMillis(w.path("start_ms"), w.path("start")); // probeer ms of sec
        long endMs   = asMillis(w.path("end_ms"),   w.path("end"));
        String text  = firstNonBlank(w.path("text"), w.path("word"), w.path("token"));
        return new TranscriptionEngine.Word(startMs, endMs, text != null ? text : "");
    }

    private static String optText(JsonNode n, String k){ return n.has(k) && !n.get(k).isNull() ? n.get(k).asText() : null; }
    private static String firstNonBlank(JsonNode... nodes){
        for (var n : nodes) if (n!=null && !n.isMissingNode() && !n.isNull() && !n.asText().isBlank()) return n.asText();
        return null;
    }
    private static long asMillis(JsonNode msField, JsonNode secField) {
        if (msField != null && msField.isNumber()) return msField.asLong();
        if (secField != null && secField.isNumber()) return Math.round(secField.asDouble() * 1000.0);
        return 0L;
    }
    private static String append(String base, String add){
        if (add == null || add.isBlank()) return base;
        if (base == null || base.isBlank()) return add.trim();
        return base + (base.endsWith(" ") ? "" : " ") + add.trim();
    }

}
