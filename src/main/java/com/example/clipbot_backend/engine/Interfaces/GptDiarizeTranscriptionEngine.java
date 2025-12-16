package com.example.clipbot_backend.engine.Interfaces;

import com.example.clipbot_backend.config.OpenAIAudioProperties;
import com.example.clipbot_backend.service.Interfaces.StorageService;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Qualifier("gptDiarizeEngine")
public class GptDiarizeTranscriptionEngine implements TranscriptionEngine {
    private static final Logger log = LoggerFactory.getLogger(GptDiarizeTranscriptionEngine.class);
    private final StorageService storage;
    private final WebClient openAiClient;
    private final OpenAIAudioProperties props;

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
        form.add("diarization", true);
        if (request.langHint() != null && !request.langHint().isBlank()) {
            form.add("language", request.langHint());
        }

        ResponseWithStatus response = openAiClient.post()
                .uri("/v1/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(form))
                .exchangeToMono(this::deserializeResponse)
                .block();

        if (response == null) {
            throw new IllegalStateException("OpenAI transcription returned no response body for objectKey=" + request.objectKey());
        }

        if (!response.status().is2xxSuccessful()) {
            log.error("OpenAI transcription failed status={} mediaId={} objectKey={} body={}",
                    response.status().value(), request.mediaId(), request.objectKey(), response.body());
            throw new IllegalStateException("OpenAI transcription failed: status=" + response.status().value()
                    + " body=" + response.body());
        }

        String json = response.body();

        var root = new ObjectMapper().readTree(json);
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
                .map(body -> new ResponseWithStatus(clientResponse.statusCode(), truncateBody(body)));
    }

    private String truncateBody(String body) {
        int max = 2_000;
        if (body == null) {
            return "";
        }
        return body.length() > max ? body.substring(0, max) + "..." : body;
    }

    private record ResponseWithStatus(HttpStatusCode status, String body) {}
}
