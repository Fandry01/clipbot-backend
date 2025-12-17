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

@Service("gptDiarizeEngine")
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
            boolean logDiarize = diarize && props.isLogDiarizeResponse();

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
                    .doOnNext(body -> {
                        if (logDiarize) {
                            LOGGER.info("OpenAI diarize raw response mediaId={} length={} snippet={}", request.mediaId(),
                                    body == null ? 0 : body.length(), truncate(body, 2000));
                        }
                    })
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

            SegmentSelection diarizeSelection = diarize ? extractDiarizeSegments(root, request) : null;
            JsonNode fallbackSegments = root.has("segments") && root.get("segments").isArray() ? root.get("segments") : null;
            JsonNode segmentsNode = diarize ? (diarizeSelection != null ? diarizeSelection.node() : null) : fallbackSegments;
            boolean hasSegments = segmentsNode != null && segmentsNode.isArray();

            if (logDiarize) {
                logDiarizeStructure(root, diarizeSelection, request);
            }

            // woorden: root.words[] of segments[].words[]
            if (!diarize && root.has("words") && root.get("words").isArray()) {
                for (var w : root.get("words")) words.add(parseWordSafe(w));
            } else if (hasSegments) {
                for (var seg : segmentsNode) {
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
                words.addAll(synthesizeWordsFromSegments(segmentsNode, request));
            }

            if (text == null && diarize && hasSegments) {
                text = buildTextFromSegments(segmentsNode);
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
                meta.put("segments", om.convertValue(segmentsNode, List.class));
            } else if (diarize) {
                List<String> fields = new ArrayList<>();
                root.fieldNames().forEachRemaining(fields::add);
                LOGGER.warn("OpenAI diarize response missing segments mediaId={} fields={}", request.mediaId(), fields);
                meta.put("diarizeMissingSegments", true);
            }

            return new TranscriptionEngine.Result(text, words, lang, "openai", meta);
        }

        private List<TranscriptionEngine.Word> synthesizeWordsFromSegments(JsonNode segments, Request request) {
            List<TranscriptionEngine.Word> synthetic = new ArrayList<>();
            int emptyTextSegments = 0;
            int missingTimeSegments = 0;
            for (var seg : segments) {
                String segText = firstNonBlank(seg, "text", "transcript", "utterance");
                segText = segText == null ? "" : segText.trim();

                OptionalLong startOpt = readTimeMs(seg, "start", "start_time");
                OptionalLong endOpt = readTimeMs(seg, "end", "end_time");

                if (segText.isBlank()) {
                    LOGGER.warn("OpenAI diarize segment missing text mediaId={} start={} end={}",
                            request.mediaId(), startOpt.orElse(-1L), endOpt.orElse(-1L));
                    emptyTextSegments++;
                    continue;
                }

                List<String> tokens = Arrays.stream(segText.split("\\s+"))
                        .filter(token -> !token.isBlank())
                        .toList();

                if (tokens.isEmpty()) {
                    LOGGER.warn("OpenAI diarize segment produced no tokens mediaId={} text='{}'", request.mediaId(), segText);
                    continue;
                }

                long segStartMs = startOpt.orElse(0L);
                long segEndMs = endOpt.orElse(segStartMs + DEFAULT_SYNTHETIC_WORD_MS * tokens.size());
                if (endOpt.isEmpty()) {
                    missingTimeSegments++;
                }
                long durationMs = Math.max(segEndMs - segStartMs, DEFAULT_SYNTHETIC_WORD_MS * tokens.size());
                double step = durationMs / (double) tokens.size();

                int segmentWords = 0;
                for (int i = 0; i < tokens.size(); i++) {
                    long tokenStart = segStartMs + Math.round(i * step);
                    long tokenEnd = segStartMs + Math.round((i + 1) * step);
                    if (tokenEnd <= tokenStart) {
                        tokenEnd = tokenStart + DEFAULT_SYNTHETIC_WORD_MS;
                    }
                    TranscriptionEngine.Word word = new TranscriptionEngine.Word(tokenStart, tokenEnd, tokens.get(i));
                    synthetic.add(word);
                    segmentWords++;
                    LOGGER.debug("OpenAI diarize synthetic word mediaId={} token='{}' startMs={} endMs={}",
                            request.mediaId(), word.text(), word.startMs(), word.endMs());
                }

                if (segmentWords == 0) {
                    LOGGER.warn("OpenAI diarize segment yielded no words mediaId={} startMs={} endMs={} text='{}'", request.mediaId(),
                            segStartMs, segEndMs, segText);
                }
            }

            if (synthetic.isEmpty()) {
                LOGGER.info("OpenAI diarize synthetic words empty mediaId={} segments={} emptyTextSegments={} missingTimeSegments={}",
                        request.mediaId(), segments.size(), emptyTextSegments, missingTimeSegments);
            }
            LOGGER.info("OpenAI diarize synthesize mediaId={} segments={} producedWords={} emptyTextSegments={} missingTimeSegments={}",
                    request.mediaId(), segments.size(), synthetic.size(), emptyTextSegments, missingTimeSegments);
            return synthetic;
        }

        private SegmentSelection extractDiarizeSegments(JsonNode root, Request request) {
            List<String> candidates = List.of("segments", "utterances", "speaker_segments", "speakerTurns", "turns");
            for (String field : candidates) {
                if (root.has(field) && root.get(field).isArray()) {
                    return new SegmentSelection(field, root.get(field));
                }
            }
            List<String> fields = new ArrayList<>();
            root.fieldNames().forEachRemaining(fields::add);
            LOGGER.warn("OpenAI diarize response missing segments array mediaId={} fields={}", request.mediaId(), fields);
            return null;
        }

        // helpers
        private static String append(String base, String add) {
            return (base == null || base.isBlank()) ? add : (base + " " + add);
        }

        private OptionalLong readTimeMs(JsonNode seg, String primaryField, String altField) {
            JsonNode node = seg.has(primaryField) && !seg.get(primaryField).isNull() ? seg.get(primaryField)
                    : (seg.has(altField) && !seg.get(altField).isNull() ? seg.get(altField) : null);
            if (node == null) return OptionalLong.empty();
            if (node.isNumber()) {
                double val = node.asDouble(Double.NaN);
                if (!Double.isFinite(val)) return OptionalLong.empty();
                if (val > 10_000) {
                    return OptionalLong.of(Math.round(val));
                }
                return OptionalLong.of(Math.round(val * 1000.0));
            }
            if (node.isTextual()) {
                try {
                    double val = Double.parseDouble(node.asText());
                    if (val > 10_000) {
                        return OptionalLong.of(Math.round(val));
                    }
                    return OptionalLong.of(Math.round(val * 1000.0));
                } catch (NumberFormatException ignored) {
                    return OptionalLong.empty();
                }
            }
            return OptionalLong.empty();
        }

        private String firstNonBlank(JsonNode node, String... fields) {
            for (String field : fields) {
                if (node.has(field) && !node.get(field).isNull()) {
                    String value = node.get(field).asText("").trim();
                    if (!value.isBlank()) return value;
                }
            }
            return null;
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
                String t = firstNonBlank(seg, "text", "transcript", "utterance");
                if (t == null) t = "";
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

        private void logDiarizeStructure(JsonNode root, SegmentSelection selection, Request request) {
            try {
                List<String> fields = new ArrayList<>();
                root.fieldNames().forEachRemaining(fields::add);
                LOGGER.info("OpenAI diarize parsed root mediaId={} fields={}", request.mediaId(), fields);

                JsonNode segments = selection == null ? null : selection.node();
                String fieldUsed = selection == null ? "<none>" : selection.field();
                int count = segments != null && segments.isArray() ? segments.size() : 0;
                LOGGER.info("OpenAI diarize segments mediaId={} fieldUsed={} count={}", request.mediaId(), fieldUsed, count);
                if (segments != null && segments.isArray() && segments.size() > 0) {
                    JsonNode first = segments.get(0);
                    String serialized = om.writeValueAsString(first);
                    List<String> segmentFields = new ArrayList<>();
                    first.fieldNames().forEachRemaining(segmentFields::add);
                    LOGGER.info("OpenAI diarize first segment mediaId={} snippet={}", request.mediaId(), truncate(serialized, 2000));
                    LOGGER.info("OpenAI diarize first segment keys mediaId={} keys={}", request.mediaId(), segmentFields);
                }
            } catch (Exception e) {
                LOGGER.info("OpenAI diarize logging failed mediaId={} error={}", request.mediaId(), e.toString());
            }
        }

        private record SegmentSelection(String field, JsonNode node) {}

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
