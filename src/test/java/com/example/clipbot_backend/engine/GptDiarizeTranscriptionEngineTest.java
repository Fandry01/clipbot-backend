package com.example.clipbot_backend.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.example.clipbot_backend.config.OpenAIAudioProperties;
import com.example.clipbot_backend.engine.Interfaces.GptDiarizeTranscriptionEngine;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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

class GptDiarizeTranscriptionEngineTest {

    private Path tempFile;

    @BeforeEach
    void setup() throws Exception {
        tempFile = Files.createTempFile("gpt-diarize", ".wav");
        Files.writeString(tempFile, "data");
    }

    @AfterEach
    void cleanup() throws Exception {
        Files.deleteIfExists(tempFile);
    }

    @Test
    void transcribeSendsDiarizedJsonWithoutWordGranularities() throws Exception {
        StorageService storage = Mockito.mock(StorageService.class);
        when(storage.resolveRaw("obj"))
                .thenReturn(tempFile);

        OpenAIAudioProperties props = new OpenAIAudioProperties();
        ExchangeStrategies strategies = ExchangeStrategies.withDefaults();

        ExchangeFunction exchangeFunction = request -> {
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

            String body = mockRequest.getBodyAsString().block();
            assertThat(body).contains("response_format")
                    .contains("diarized_json")
                    .contains("chunking_strategy")
                    .contains("auto")
                    .contains("diarization")
                    .doesNotContain("timestamp_granularities[]")
                    .doesNotContain("word");
            assertThat(mockRequest.getHeaders().getContentType()).isEqualTo(MediaType.MULTIPART_FORM_DATA);

            String responseJson = "{\"text\":\"hi\",\"language\":\"en\",\"segments\":[]}";
            ClientResponse response = ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(responseJson)
                    .build();
            return Mono.just(response);
        };

        WebClient client = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();

        TranscriptionEngine engine = new GptDiarizeTranscriptionEngine(storage, client, props);
        engine.transcribe(new TranscriptionEngine.Request(UUID.randomUUID(), "obj", "en"));
    }

    @Test
    void nonSuccessResponsesIncludeStatusAndBody() throws Exception {
        StorageService storage = Mockito.mock(StorageService.class);
        when(storage.resolveRaw("obj"))
                .thenReturn(tempFile);

        OpenAIAudioProperties props = new OpenAIAudioProperties();

        ExchangeFunction exchangeFunction = request -> {
            String errorJson = "{\"error\":\"bad\"}";
            ClientResponse response = ClientResponse.create(HttpStatus.BAD_REQUEST)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(errorJson)
                    .build();
            return Mono.just(response);
        };

        WebClient client = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();

        TranscriptionEngine engine = new GptDiarizeTranscriptionEngine(storage, client, props);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> engine.transcribe(new TranscriptionEngine.Request(UUID.randomUUID(), "obj", "en")));

        assertThat(ex.getMessage()).contains("status=400")
                .contains("body=")
                .contains("{\"error\":\"bad\"}");
    }

    @Test
    void retriesAndSurfacesTruncatedResponses() throws Exception {
        StorageService storage = Mockito.mock(StorageService.class);
        when(storage.resolveRaw("obj"))
                .thenReturn(tempFile);

        OpenAIAudioProperties props = new OpenAIAudioProperties();
        ExchangeStrategies strategies = ExchangeStrategies.withDefaults();
        AtomicInteger attempts = new AtomicInteger();

        ExchangeFunction exchangeFunction = request -> {
            attempts.incrementAndGet();
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
                    .body("{\"text\":\"hi\"") // truncated
                    .build();
            return Mono.just(response);
        };

        WebClient client = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();

        TranscriptionEngine engine = new GptDiarizeTranscriptionEngine(storage, client, props);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> engine.transcribe(new TranscriptionEngine.Request(UUID.randomUUID(), "obj", "en")));

        assertThat(ex.getMessage()).contains("OPENAI_TRUNCATED_RESPONSE");
        assertThat(attempts.get()).isEqualTo(3); // initial + 2 retries
    }
}
