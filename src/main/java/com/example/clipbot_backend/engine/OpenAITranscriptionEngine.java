package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.config.OpenAIAudioProperties;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
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
        private static final int RETRY_MAX_ATTEMPTS = 2;
        private static final Duration MIN_BLOCK_TIMEOUT = Duration.ofMinutes(45);
        private static final long DEFAULT_SYNTHETIC_WORD_MS = 200L;
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
                form.add("chunking_strategy", "auto");
                LOGGER.info("OpenAI diarization request mediaId={} responseFormat=diarized_json chunking_strategy=auto", request.mediaId());
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
                    .bodyToMono(String.class)
                    .map(body -> parseJson(body, request))
                    .retryWhen(Retry.backoff(RETRY_MAX_ATTEMPTS, RETRY_BACKOFF)
                            .filter(this::isRetryable)
                            .doBeforeRetry(signal -> {
                                Throwable failure = signal.failure();
                                Throwable root = rootCause(failure);
                                LOGGER.warn(
                                        "OpenAI ASR retry attempt={} mediaId={} type={} rootCause={} message={}",
                                        signal.totalRetriesInARow() + 1,
                                        request.mediaId(),
                                        failure == null ? "unknown" : failure.getClass().getSimpleName(),
                                        root == null ? "unknown" : root.getClass().getSimpleName(),
                                        root == null ? "" : root.getMessage()
                                );
                            }));

            Duration blockTimeout = Duration.ofSeconds(Math.max(props.getTimeoutSeconds(), MIN_BLOCK_TIMEOUT.toSeconds()));
            JsonNode root = mono.block(blockTimeout);

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

            if (diarize && words.isEmpty() && hasSegments) {
                words.addAll(synthesizeWordsFromSegments(root.get("segments")));
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

        private List<TranscriptionEngine.Word> synthesizeWordsFromSegments(JsonNode segments) {
            List<TranscriptionEngine.Word> synthetic = new ArrayList<>();
            for (var seg : segments) {
                String segText = seg.path("text").asText("").trim();
                if (segText.isBlank()) continue;

                List<String> tokens = Arrays.stream(segText.split("\\s+"))
                        .filter(token -> !token.isBlank())
                        .toList();
                if (tokens.isEmpty()) continue;

                double start = seg.path("start").asDouble(Double.NaN);
                double end = seg.path("end").asDouble(Double.NaN);
                long segStartMs = Double.isFinite(start) ? Math.round(start * 1000.0) : 0L;
                long segEndMs = Double.isFinite(end) ? Math.round(end * 1000.0) : segStartMs + DEFAULT_SYNTHETIC_WORD_MS * tokens.size();
                long durationMs = Math.max(segEndMs - segStartMs, DEFAULT_SYNTHETIC_WORD_MS * tokens.size());
                double step = durationMs / (double) tokens.size();

                for (int i = 0; i < tokens.size(); i++) {
                    long tokenStart = segStartMs + Math.round(i * step);
                    long tokenEnd = segStartMs + Math.round((i + 1) * step);
                    if (tokenEnd <= tokenStart) {
                        tokenEnd = tokenStart + DEFAULT_SYNTHETIC_WORD_MS;
                    }
                    synthetic.add(new TranscriptionEngine.Word(tokenStart, tokenEnd, tokens.get(i)));
                }
            }
            return synthetic;
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

        private JsonNode parseJson(String body, Request request) {
            try {
                return om.readTree(body);
            } catch (JsonProcessingException e) {
                String snippet = truncate(body, 500);
                int length = body == null ? 0 : body.length();
                LOGGER.warn("OpenAI ASR parse failure mediaId={} length={} snippet={}", request.mediaId(), length, snippet);
                throw new OpenAITruncatedResponseException("OPENAI_TRUNCATED_RESPONSE length=" + length + " snippet=" + snippet,
                        e);
            }
        }

        private static String truncate(String body, int max) {
            if (body == null) return "";
            if (body.length() <= max) return body;
            return body.substring(0, max) + "...";
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
            if (throwable == null) return false;
            if (hasCause(throwable, PrematureCloseException.class)) return true;
            if (hasCause(throwable, OpenAITruncatedResponseException.class)) return true;
            if (hasBadRecordMac(throwable)) return true;
            return hasDecoderWithBadRecordMac(throwable);
        }

        private boolean hasBadRecordMac(Throwable throwable) {
            Throwable cursor = throwable;
            while (cursor != null) {
                if (cursor instanceof javax.net.ssl.SSLException ssl) {
                    String msg = ssl.getMessage();
                    if (msg != null && msg.toLowerCase(Locale.ROOT).contains("bad_record_mac")) {
                        return true;
                    }
                }
                cursor = cursor.getCause();
            }
            return false;
        }

        private boolean hasDecoderWithBadRecordMac(Throwable throwable) {
            Throwable cursor = throwable;
            while (cursor != null) {
                if (cursor instanceof io.netty.handler.codec.DecoderException decoder && decoder.getCause() != null) {
                    if (hasBadRecordMac(decoder.getCause())) {
                        return true;
                    }
                }
                cursor = cursor.getCause();
            }
            return false;
        }

        private boolean hasCause(Throwable throwable, Class<? extends Throwable> target) {
            Throwable cursor = throwable;
            while (cursor != null) {
                if (target.isInstance(cursor)) return true;
                cursor = cursor.getCause();
            }
            return false;
        }

        private Throwable rootCause(Throwable throwable) {
            Throwable cursor = throwable;
            Throwable prev = null;
            while (cursor != null && cursor != prev) {
                prev = cursor;
                cursor = cursor.getCause();
            }
            return prev;
        }

        private static class OpenAITruncatedResponseException extends RuntimeException {
            OpenAITruncatedResponseException(String message, Throwable cause) {
                super(message, cause);
            }
        }
    }
