package com.example.clipbot_backend.engine.Interfaces;

import com.example.clipbot_backend.config.OpenAIAudioProperties;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;
import reactor.util.retry.Retry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Qualifier("gptDiarizeEngine")
public class GptDiarizeTranscriptionEngine implements TranscriptionEngine {
    private static final Logger log = LoggerFactory.getLogger(GptDiarizeTranscriptionEngine.class);
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(200);
    private static final int RETRY_MAX_ATTEMPTS = 2;
    private final StorageService storage;
    private final WebClient openAiClient;
    private final OpenAIAudioProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GptDiarizeTranscriptionEngine(StorageService storage, @Qualifier("openAiWebClient")WebClient openAiClient, OpenAIAudioProperties props) {
        this.storage = storage;
        this.openAiClient = openAiClient;
        this.props = props;
    }

    @Override
    public Result transcribe(Request request) throws Exception {
        Path input = storage.resolveRaw(request.objectKey());
        if (!Files.exists(input)) throw new IllegalArgumentException("input not found: " + input);

        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new FileSystemResource(input));
        form.add("model", props.getModel());
        form.add("response_format", "diarized_json");
        form.add("chunking_strategy", "auto");
        form.add("diarization", true);
        if (request.langHint() != null && !request.langHint().isBlank()) {
            form.add("language", request.langHint());
        }

        Mono<JsonNode> mono = openAiClient.post()
                .uri("/v1/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(form))
                .exchangeToMono(this::deserializeResponse)
                .flatMap(resp -> {
                    if (!resp.status().is2xxSuccessful()) {
                        log.error("OpenAI transcription failed status={} mediaId={} objectKey={} body={}",
                                resp.status().value(), request.mediaId(), request.objectKey(), truncate(resp.body(), 2_000));
                        return Mono.error(new IllegalStateException("OpenAI transcription failed: status=" + resp.status().value()
                                + " body=" + truncate(resp.body(), 2_000)));
                    }
                    try {
                        return Mono.just(parseJson(resp.body(), request));
                    } catch (RuntimeException ex) {
                        return Mono.error(ex);
                    }
                })
                .retryWhen(Retry.backoff(RETRY_MAX_ATTEMPTS, RETRY_BACKOFF)
                        .filter(this::isRetryable)
                        .doBeforeRetry(signal -> log.warn(
                                "GPT diarize retry attempt={} mediaId={} cause={}",
                                signal.totalRetriesInARow() + 1,
                                request.mediaId(),
                                signal.failure() == null ? "unknown" : signal.failure().toString()
                        )));

        JsonNode root = mono.block();
        String text = root.path("text").asText("");
        String lang = root.path("language").asText("auto");

        List<Map<String,Object>> segments = new ArrayList<>();
        if (root.has("segments") && root.get("segments").isArray()) {
            for (var s : root.get("segments")) {
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("speaker", s.path("speaker").asText("speaker_0"));
                m.put("startMs", Math.round(s.path("start").asDouble(0) * 1000));
                m.put("endMs",   Math.round(s.path("end").asDouble(0) * 1000));
                m.put("text",    s.path("text").asText(""));
                segments.add(m);
            }
        }

        if (text.isBlank() && !segments.isEmpty()) {
            text = segments.stream()
                    .map(m -> m.getOrDefault("text", "").toString())
                    .filter(str -> !str.isBlank())
                    .reduce((a, b) -> a + " " + b)
                    .orElse("");
        }

        Map<String,Object> meta = new LinkedHashMap<>();
        meta.put("schema", "diarized_json");
        meta.put("segments", segments);
        meta.put("providerModel", props.getModel());
        meta.put("diarization", true);
        meta.put("timestampGranularities", List.of("segment"));

        return new Result(text, List.of(), lang, "GPT_DIARIZE", meta);
    }

    private Mono<ResponseWithStatus> deserializeResponse(ClientResponse clientResponse) {
        return clientResponse.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> new ResponseWithStatus(clientResponse.statusCode(), body));
    }

    private record ResponseWithStatus(HttpStatusCode status, String body) {}

    private JsonNode parseJson(String body, Request request) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            String snippet = truncate(body, 500);
            int length = body == null ? 0 : body.length();
            log.warn("GPT diarize parse failure mediaId={} length={} snippet={}", request.mediaId(), length, snippet);
            throw new OpenAITruncatedResponseException("OPENAI_TRUNCATED_RESPONSE length=" + length + " snippet=" + snippet, e);
        }
    }

    private static String truncate(String body, int max) {
        if (body == null) return "";
        if (body.length() <= max) return body;
        return body.substring(0, max) + "...";
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof PrematureCloseException) {
            return true;
        }
        if (throwable instanceof org.springframework.web.reactive.function.client.WebClientRequestException reqEx
                && reqEx.getCause() instanceof PrematureCloseException) {
            return true;
        }
        if (throwable instanceof OpenAITruncatedResponseException) {
            return true;
        }
        return false;
    }

    private static class OpenAITruncatedResponseException extends RuntimeException {
        OpenAITruncatedResponseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
