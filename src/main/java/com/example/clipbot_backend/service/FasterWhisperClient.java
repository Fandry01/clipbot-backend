package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.FwVerboseResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Path;
import java.time.Duration;

@Service
public class FasterWhisperClient {
    private final WebClient client;
    private final String model;
    private final Duration timeout;

    public FasterWhisperClient(@Qualifier("fwWebClient") WebClient client, @Value("${fw.model}") String model, @Value("${fw.timeout-seconds}") Duration timeout) {
        this.client = client;
        this.model = model;
        this.timeout = timeout;
    }

    public FwVerboseResponse transcribeFile(Path file, boolean wordTs) {
        var mb = new LinkedMultiValueMap<String, Object>();
        mb.add("file", new FileSystemResource(file));
        mb.add("model", model);
        mb.add("response_format", "verbose_json");
        mb.add("word_timestamps", String.valueOf(wordTs));

        return client.post()
                .uri("/v1/audio/transcriptions")
                .body(BodyInserters.fromMultipartData(mb))
                .retrieve()
                .bodyToMono(FwVerboseResponse.class)
                .block(timeout);
    }
}

