package com.example.clipbot_backend.service;

import com.example.clipbot_backend.AsrException;
import com.example.clipbot_backend.config.FwProperties;
import com.example.clipbot_backend.dto.FwVerboseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Path;
import java.time.Duration;

@Component
public class FasterWhisperClient {

    private final WebClient client;
    private final String model; // alleen voor het form-veld
    private static final Logger log = LoggerFactory.getLogger(FasterWhisperClient.class);
    private final Duration timeout;

    public FasterWhisperClient(@Qualifier("fwWebClient") WebClient client, FwProperties props) {
        this.client = client;
        this.model = props.getModel(); // of null laten en server default gebruiken
        this.timeout = Duration.ofSeconds(props.getTimeoutSeconds());
    }

    public FwVerboseResponse transcribeFile(Path file, boolean wordTs) {
        var mb = new LinkedMultiValueMap<String, Object>();
        mb.add("file", new FileSystemResource(file));
        if (model != null && !model.isBlank()) {
            mb.add("model", model); // kan weg als ASR_MODEL op de server staat
        }
        mb.add("response_format", "verbose_json");
        if (wordTs) {
            mb.add("word_timestamps", "true");
            mb.add("timestamp_granularities[]", "word");
        }

        long start = System.currentTimeMillis();
        return client.post()
                .uri("/v1/audio/transcriptions")
                .body(BodyInserters.fromMultipartData(mb))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class)
                                .map(body -> new AsrException("FasterWhisper error " + resp.statusCode() + ": " + body)))
                .bodyToMono(FwVerboseResponse.class)
                .timeout(timeout)
                .doOnSuccess(r -> log.debug("FW {} processed in {} ms",
                        file.getFileName(), System.currentTimeMillis() - start))
                .block();
    }
}


