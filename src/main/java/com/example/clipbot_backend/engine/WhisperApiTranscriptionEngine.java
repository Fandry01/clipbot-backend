package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.config.ApiAsrProperties;
import com.example.clipbot_backend.dto.web.TranscriptionResult;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.service.StorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class WhisperApiTranscriptionEngine implements TranscriptionEngine {
    private final StorageService storageService;
    private final WebClient apiClient;
    private final WebClient rawClient;
    private final ObjectMapper objectMapper;
    private final ApiAsrProperties properties;
    private  final Path workDir;

    public WhisperApiTranscriptionEngine(StorageService storageService, WebClient apiClient, WebClient rawClient, ObjectMapper objectMapper, ApiAsrProperties properties, String workDir) {
        this.storageService = storageService;
        this.apiClient = apiClient;
        this.rawClient = rawClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.workDir = Path.of(workDir)
                .toAbsolutePath()
                .normalize();
        try { Files.createDirectories(this.workDir); } catch (Exception ignore) {}
    }

    @Override
    public TranscriptionResult transcribe(Path mediaFile, String langHint) throws Exception {
        return null;
    }

    @Override
    public Result transcribe(Request request) throws Exception{
        Path input = storageService.resolveRaw(request.objectKey());
        if(!Files.exists(input)) throw new IllegalArgumentException("Input not found: " + input);

        return switch(properties.getUploadMode()){
            case DIRECT -> directFlow(request, input);
            case PRESIGNED -> presignedFlow(request,input);
        };
    }

    private Result directFlow(Request request, Path input) throws Exception {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new FileSystemResource(input));
        form.add("model", properties.getModel());
        form.add("language", request.langHint());
        form.add("response_format", "json");
        form.add("timestamps","word");

        String body = apiClient.post().uri(properties.getTranscribePath())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(form))
                .retrieve().onStatus(status -> !status.is2xxSuccessful(),
                        reaction -> reaction.bodyToMono(String.class).flatMap(b -> Mono.error(new RuntimeException("ASR(direct) non-2xx: " + b))))
                .bodyToMono(String.class)
                .block(Duration.ofMinutes(properties.getTimeoutSeconds()));


        JsonNode root = objectMapper.readTree(body);
        if(isCompletedResult(root)){
            return parseFinalResult(root);
        }
        String jobId = extractJobId(root);
        return pollUntilDone(jobId);

    }

    private Result presignedFlow(Request request, Path input) throws Exception {
        Map<String, Object> payload = Map.of(
                "model", properties.getModel(),
                "language", request.langHint(),
                "response_format","json",
                "timestamps", "word"
        );

        String created = apiClient.post()
                .uri(properties.getTranscribePath())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(payload))
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(),
                        reaction -> reaction.bodyToMono(String.class)
                                .flatMap(b -> Mono.error(new RuntimeException("ASR(PRESIGNED create) non-2xx: " + b))))
                .bodyToMono(String.class)
                                .block(Duration.ofSeconds(properties.getTimeoutSeconds()));

        JsonNode createRoot = objectMapper.readTree(created);
        String jobId = extractJobId(createRoot);
        String uploadUrl = extractUploadUrl(createRoot);
        if(uploadUrl == null) throw new RuntimeException("Missing uploadUrl in create response");

        // upload bestand via PUT (stream in file)
        rawClient.put().uri(uploadUrl).contentType(MediaType.APPLICATION_OCTET_STREAM).headers( header ->{
            if(properties.getUploadHeaderName() != null && properties.getUploadHeaderValue() != null){
               header.set(properties.getUploadHeaderName(), properties.getUploadHeaderValue());
            }
        }).body(BodyInserters.fromResource(new FileSystemResource(input)))
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(),
                        r -> r.bodyToMono(String.class)
                                .flatMap(b -> Mono.error(new RuntimeException("Upload PUT non-2xx: " + b))))
                .toBodilessEntity()
                .block(Duration.ofSeconds(properties.getTimeoutSeconds()));

        return pollUntilDone(jobId);
    }

    private Result pollUntilDone(String jobId) throws Exception{
        int attempts = 0;
        while (attempts++ < properties.getPollMaxAttempts()){
            String body = apiClient.get()
                    .uri(properties.getStatusPath().replace("{id}", jobId))
                    .retrieve()
                    .onStatus(s -> !s.is2xxSuccessful(),
                            r -> r.bodyToMono(String.class).flatMap(b -> Mono.error(new RuntimeException("ASR status non-2xx: " + b))))
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(properties.getTimeoutSeconds()));
            JsonNode root = objectMapper.readTree(body);

            if (isCompletedResult(root)) return parseFinalResult(root);
            if (isFailed(root)) throw new RuntimeException("ASR job failed: " + root.toString());
            TimeUnit.MILLISECONDS.sleep(properties.getPollIntervalMs());
        }
        throw new RuntimeException("ASR job timeout after " + (properties.getPollIntervalMs() * properties.getPollMaxAttempts()) + " ms");

    }
    private boolean isCompletedResult(JsonNode root) {
        String status = asText(root.path("status"));
        if ("completed".equalsIgnoreCase(status) || "done".equalsIgnoreCase(status) || "succeeded".equalsIgnoreCase(status))
            return true;
        return root.has("text") || root.has("segments") || root.has("words");
    }

    private boolean isFailed(JsonNode root) {
        String status = asText(root.path("status"));
        return "failed".equalsIgnoreCase(status) || root.has("error");
    }

    private String extractJobId(JsonNode root) {
        String id = asText(root.path("id"));
        if (id != null) return id;
        id = asText(root.path("jobId"));
        if (id != null) return id;
        JsonNode data = root.path("data");
        if (data.isObject()) {
            id = asText(data.path("id"));
            if (id != null) return id;
            id = asText(data.path("jobId"));
            if (id != null) return id;
        }
        throw new RuntimeException("Cannot extract jobId: " + root);
    }

    private String extractUploadUrl(JsonNode root) {
        String u = asText(root.path("uploadUrl"));
        if (u != null) return u;
        JsonNode data = root.path("data");
        if (data.isObject()) {
            u = asText(data.path("uploadUrl"));
            if (u != null) return u;
        }
        return null;
    }

    private Result parseFinalResult(JsonNode root) {
        String lang = pick(List.of(asText(root.path(properties.getJsonLangPath())),
                asText(root.path("language")), asText(root.path("lang"))));
        String text = pick(List.of(asText(root.path(properties.getJsonTextPath())),
                asText(root.path("text")), asText(root.path("transcript"))));

        List<Word> words = new ArrayList<>();

        if (root.has("words") && root.get("words").isArray()) {
            for (JsonNode w : root.get("words")) words.add(parseWord(w));
        }
        if (words.isEmpty() && root.has("segments") && root.get("segments").isArray()) {
            for (JsonNode seg : root.get("segments")) {
                if (seg.has("words")) for (JsonNode w : seg.get("words")) words.add(parseWord(w));
                if (text == null && seg.has("text")) text = append(text, seg.get("text").asText());
                if (lang == null && seg.has("language")) lang = seg.get("language").asText();
            }
        }

        if (text == null) text = "";
        if (lang == null) lang = "auto";
        Map<String, Object> meta = Map.of("provider", "asr-api");
        return new Result(text, words, lang, "asr-api", meta);
    }

    private Word parseWord(JsonNode w) {
        long startMs = asMillis(w.path("startMs"), w.path("start"));
        long endMs   = asMillis(w.path("endMs"), w.path("end"));
        String text  = pick(List.of(asText(w.path("text")), asText(w.path("word")), asText(w.path("token"))));
        if (text == null) text = "";
        return new Word(startMs, endMs, text);
    }

    private static String asText(JsonNode n) {
        return (n != null && !n.isMissingNode() && !n.isNull()) ? n.asText() : null;
    }
    private static String pick(List<String> opts) {
        for (String s : opts) if (s != null && !s.isBlank()) return s;
        return null;
    }
    private long asMillis(JsonNode msField, JsonNode secField) {
        if (msField != null && msField.isNumber()) return msField.asLong();
        if (secField != null && secField.isNumber()) return Math.round(secField.asDouble() * 1000.0);
        return 0L;
    }
    private static String append(String base, String add) {
        if (add == null || add.isBlank()) return base;
        if (base == null || base.isBlank()) return add;
        return base + (base.endsWith(" ") ? "" : " ") + add.trim();
    }
}
