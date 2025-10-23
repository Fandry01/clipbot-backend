package com.example.clipbot_backend.service;

import com.example.clipbot_backend.config.FwProperties;
import com.example.clipbot_backend.dto.FwVerboseResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Path;

@Component
public class FasterWhisperClient {

    private final WebClient client;
    private final String model; // alleen voor het form-veld

    public FasterWhisperClient(@Qualifier("fwWebClient") WebClient client, FwProperties props) {
        this.client = client;
        this.model = props.getModel(); // of null laten en server default gebruiken
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

        return client.post()
                .uri("/v1/audio/transcriptions")
                .body(BodyInserters.fromMultipartData(mb))
                .retrieve()
                .onStatus(s -> s.isError(), resp -> resp.bodyToMono(String.class)
                        .map(body -> new RuntimeException("FW error " + resp.statusCode() + ": " + body)))
                .bodyToMono(FwVerboseResponse.class)
                .block(); // timeout wordt al afgedwongen door fwWebClient (Netty timeouts)
    }
}


