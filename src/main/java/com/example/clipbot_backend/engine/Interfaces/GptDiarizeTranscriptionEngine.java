package com.example.clipbot_backend.engine.Interfaces;

import com.example.clipbot_backend.config.OpenAIAudioProperties;
import com.example.clipbot_backend.service.Interfaces.StorageService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Qualifier("gptDiarizeEngine")
public class GptDiarizeTranscriptionEngine implements TranscriptionEngine {
    private final StorageService storage;
    private final WebClient openAiClient;
    private final OpenAIAudioProperties props;
    private final Duration timeout;

    public GptDiarizeTranscriptionEngine(StorageService storage, @Qualifier("openAiWebClient")WebClient openAiClient, OpenAIAudioProperties props, Duration timeout) {
        this.storage = storage;
        this.openAiClient = openAiClient;
        this.props = props;
        this.timeout = timeout;
    }

    @Override
    public Result transcribe(Request request) throws Exception {
        Path input = storage.resolveRaw(request.objectKey());
        if (!Files.exists(input)) throw new IllegalArgumentException("input not found: " + input);

        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new FileSystemResource(input));
        form.add("model", "gpt-4o-transcribe-diarize");
        form.add("response_format", "diarized_json");
        if (request.langHint() != null && !request.langHint().isBlank()) {
            form.add("language", request.langHint());
        }

        String json = openAiClient.post()
                .uri("/v1/audio/transcriptions")
                .body(BodyInserters.fromMultipartData(form))
                .retrieve()
                .bodyToMono(String.class)
                .block(timeout);

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

        Map<String,Object> meta = new LinkedHashMap<>();
        meta.put("schema", "diarized_json");
        meta.put("segments", segments);
        meta.put("providerModel", "gpt-4o-transcribe-diarize");

        return new Result(text, List.of(), lang, "GPT_DIARIZE", meta);
    }
}
