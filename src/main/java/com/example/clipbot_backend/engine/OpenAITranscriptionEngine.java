package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.config.OpenAIAudioProperties;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

@Service
    public class OpenAITranscriptionEngine implements TranscriptionEngine {
        private final StorageService storageService;
        private final WebClient client;
        private final OpenAIAudioProperties props;
        private final ObjectMapper om = new ObjectMapper();

        public OpenAITranscriptionEngine(StorageService storageService,
                                         @Qualifier("openAiWebClient") WebClient client, // ← fix Qualifier
                                         OpenAIAudioProperties props) {
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
            form.add("model", props.getModel());

            String langHint = request.langHint() != null && !request.langHint().isBlank()
                    ? request.langHint().toLowerCase(Locale.ROOT)
                    : (props.getLanguage() == null ? null : props.getLanguage().toLowerCase(Locale.ROOT));
            if (langHint != null && !langHint.isBlank()) form.add("language", langHint);

            form.add("response_format", "verbose_json");
            form.add("timestamp_granularities[]", "word"); // genegeerd? geen probleem

            JsonNode root = client.post()
                    .uri("/v1/audio/transcriptions")
                    .body(BodyInserters.fromMultipartData(form))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, resp ->
                            resp.bodyToMono(String.class).defaultIfEmpty("")
                                    .map(body -> new RuntimeException("OpenAI ASR error %s: %s".formatted(resp.statusCode(), body))))
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(props.getTimeoutSeconds()));

            if (root == null) throw new RuntimeException("Empty response from OpenAI ASR");

            String text = optText(root, "text");
            String lang = optText(root, "language"); // kan ontbreken
            List<TranscriptionEngine.Word> words = new ArrayList<>();

            // woorden: root.words[] of segments[].words[]
            if (root.has("words") && root.get("words").isArray()) {
                for (var w : root.get("words")) words.add(parseWordSafe(w));
            } else if (root.has("segments") && root.get("segments").isArray()) {
                for (var seg : root.get("segments")) {
                    if (seg.has("words")) {
                        for (var w : seg.get("words")) words.add(parseWordSafe(w));
                    }
                    if ((text == null || text.isBlank()) && seg.has("text")) {
                        text = append(text, seg.get("text").asText(""));
                    }
                    if ((lang == null || lang.isBlank()) && seg.has("language")) {
                        lang = seg.get("language").asText("");
                    }
                }
            }
            if (text == null) text = "";
            if (lang == null || lang.isBlank()) lang = "auto";
            lang = lang.toLowerCase(Locale.ROOT);

            // clamp + sort
            words = words.stream()
                    .map(w -> new TranscriptionEngine.Word(
                            Math.max(0L, w.startMs()),
                            Math.max(Math.max(0L, w.startMs()), w.endMs()),
                            w.text()))
                    .sorted(Comparator.comparingLong(TranscriptionEngine.Word::startMs))
                    .toList();

            // meta + segments (belangrijk voor TranscriptService.upsert)
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("provider", "openai");
            meta.put("model", props.getModel());
            if (root.has("segments") && root.get("segments").isArray()) {
                meta.put("segments", om.convertValue(root.get("segments"), List.class));
            }

            return new TranscriptionEngine.Result(text, words, lang, "openai", meta);
        }

        // helpers
        private static String append(String base, String add) {
            return (base == null || base.isBlank()) ? add : (base + " " + add);
        }

        private TranscriptionEngine.Word parseWordSafe(JsonNode w) {
            double s = w.path("start").asDouble(Double.NaN);
            double e = w.path("end").asDouble(Double.NaN);
            long startMs = Double.isFinite(s) ? Math.round(s * 1000.0) : 0L;
            long endMs   = Double.isFinite(e) ? Math.round(e * 1000.0) : startMs;
            String text  = w.path("word").asText(w.path("text").asText(""));
            return new TranscriptionEngine.Word(startMs, endMs, text);
        }

        private static String optText(JsonNode node, String field) {
            return (node.has(field) && !node.get(field).isNull() && !node.get(field).asText().isBlank())
                    ? node.get(field).asText() : null;
        }
    }
