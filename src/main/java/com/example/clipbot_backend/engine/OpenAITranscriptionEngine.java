package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.config.OpenAIAudioProperties;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;
import reactor.util.retry.Retry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

@Service
    public class OpenAITranscriptionEngine implements TranscriptionEngine {
        private static final Logger LOGGER = LoggerFactory.getLogger(OpenAITranscriptionEngine.class);
        private static final Duration RETRY_BACKOFF = Duration.ofMillis(200);
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

            boolean diarize = props.isDiarize();

            String langHint = request.langHint() != null && !request.langHint().isBlank()
                    ? request.langHint().toLowerCase(Locale.ROOT)
                    : (props.getLanguage() == null ? null : props.getLanguage().toLowerCase(Locale.ROOT));
            if (langHint != null && !langHint.isBlank()) form.add("language", langHint);

            if (diarize) {
                form.add("response_format", "diarized_json");
            } else {
                form.add("response_format", "verbose_json");
                form.add("timestamp_granularities[]", "word");
            }

            Mono<JsonNode> mono = client.post()
                    .uri("/v1/audio/transcriptions")
                    .body(BodyInserters.fromMultipartData(form))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, resp ->
                            resp.bodyToMono(String.class).defaultIfEmpty("")
                                    .map(body -> new RuntimeException("OpenAI ASR error %s: %s".formatted(resp.statusCode(), body))))
                    .bodyToMono(JsonNode.class)
                    .retryWhen(Retry.backoff(3, RETRY_BACKOFF)
                            .filter(this::isRetryable)
                            .doBeforeRetry(signal -> LOGGER.warn(
                                    "OpenAI ASR retry attempt={} mediaId={} cause={}",
                                    signal.totalRetriesInARow() + 1,
                                    request.mediaId(),
                                    signal.failure() == null ? "unknown" : signal.failure().toString()
                            )));

            JsonNode root = mono.block(Duration.ofSeconds(props.getTimeoutSeconds()));

            if (root == null) throw new RuntimeException("Empty response from OpenAI ASR");

            String text = optText(root, "text");
            String lang = optText(root, "language"); // kan ontbreken
            List<TranscriptionEngine.Word> words = new ArrayList<>();

            boolean hasSegments = root.has("segments") && root.get("segments").isArray();

            // woorden: root.words[] of segments[].words[]
            if (!diarize && root.has("words") && root.get("words").isArray()) {
                for (var w : root.get("words")) words.add(parseWordSafe(w));
            } else if (hasSegments) {
                for (var seg : root.get("segments")) {
                    if (!diarize && seg.has("words")) {
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

            if (text == null && diarize && hasSegments) {
                text = buildTextFromSegments(root.get("segments"));
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
            if (hasSegments) {
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

        private String buildTextFromSegments(JsonNode segments) {
            StringBuilder sb = new StringBuilder();
            for (var seg : segments) {
                String t = seg.path("text").asText("");
                if (!t.isBlank()) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(t.trim());
                }
            }
            return sb.toString();
        }

        private static String optText(JsonNode node, String field) {
            return (node.has(field) && !node.get(field).isNull() && !node.get(field).asText().isBlank())
                    ? node.get(field).asText() : null;
        }

        private boolean isRetryable(Throwable throwable) {
            if (throwable instanceof PrematureCloseException) {
                return true;
            }
            if (throwable instanceof WebClientRequestException reqEx) {
                return reqEx.getCause() instanceof PrematureCloseException;
            }
            return false;
        }
    }
