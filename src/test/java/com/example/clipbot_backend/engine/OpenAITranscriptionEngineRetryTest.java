package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.config.OpenAIAudioProperties;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.codec.HttpMessageWriter;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class OpenAITranscriptionEngineRetryTest {

    private Path tempFile;

    @BeforeEach
    void setup() throws Exception {
        tempFile = Files.createTempFile("openai-retry", ".wav");
        Files.writeString(tempFile, "data");
    }

    @AfterEach
    void cleanup() throws Exception {
        Files.deleteIfExists(tempFile);
    }

    @Test
    void retriesOnPrematureCloseException() throws Exception {
        StorageService storage = Mockito.mock(StorageService.class);
        when(storage.resolveRaw("obj")).thenReturn(tempFile);

        OpenAIAudioProperties props = new OpenAIAudioProperties();
        ExchangeStrategies strategies = ExchangeStrategies.withDefaults();
        AtomicInteger attempts = new AtomicInteger();

        ExchangeFunction exchangeFunction = request -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                return Mono.error(new PrematureCloseException("closed"));
            }

            MockClientHttpRequest mockRequest = new MockClientHttpRequest(request.method(), request.url());
            request.body().insert(mockRequest, new BodyInserter.Context() {
                @Override
                public List<HttpMessageWriter<?>> messageWriters() {
                    return strategies.messageWriters();
                }

                @Override
                public Optional<ServerHttpRequest> serverRequest() {
                    return Optional.empty();
                }

                @Override
                public Map<String, Object> hints() {
                    return Map.of();
                }
            }).block();

            ClientResponse response = ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"text\":\"ok\",\"language\":\"en\",\"segments\":[]}")
                    .build();
            return Mono.just(response);
        };

        WebClient client = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();

        TranscriptionEngine engine = new OpenAITranscriptionEngine(storage, client, props);
        TranscriptionEngine.Result result = engine.transcribe(new TranscriptionEngine.Request(UUID.randomUUID(), "obj", "en"));

        assertThat(attempts.get()).isEqualTo(3);
        assertThat(result.text()).isEqualTo("ok");
    }

    @Test
    void doesNotRetryOn4xxErrors() throws Exception {
        StorageService storage = Mockito.mock(StorageService.class);
        when(storage.resolveRaw("obj")).thenReturn(tempFile);

        OpenAIAudioProperties props = new OpenAIAudioProperties();
        AtomicInteger attempts = new AtomicInteger();

        ExchangeFunction exchangeFunction = request -> {
            attempts.incrementAndGet();
            ClientResponse response = ClientResponse.create(HttpStatus.BAD_REQUEST)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"error\":\"bad\"}")
                    .build();
            return Mono.just(response);
        };

        WebClient client = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();

        TranscriptionEngine engine = new OpenAITranscriptionEngine(storage, client, props);

        assertThrows(RuntimeException.class,
                () -> engine.transcribe(new TranscriptionEngine.Request(UUID.randomUUID(), "obj", "en")));
        assertThat(attempts.get()).isEqualTo(1);
    }
}
