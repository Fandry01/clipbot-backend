package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.config.OpenAIAudioProperties;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.codec.HttpMessageWriter;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAITranscriptionEngineFormatTest {

    private Path tempFile;

    @BeforeEach
    void setup() throws Exception {
        tempFile = Files.createTempFile("openai-format", ".wav");
        Files.writeString(tempFile, "data");
    }

    @AfterEach
    void cleanup() throws Exception {
        Files.deleteIfExists(tempFile);
    }

    @Test
    void diarizeRequestsUseDiarizedJsonWithoutWordGranularity() throws Exception {
        StorageService storage = Mockito.mock(StorageService.class);
        Mockito.when(storage.resolveRaw("obj")).thenReturn(tempFile);

        OpenAIAudioProperties props = new OpenAIAudioProperties();
        props.setDiarize(true);

        ExchangeStrategies strategies = ExchangeStrategies.withDefaults();
        AtomicReference<String> bodyRef = new AtomicReference<>();

        ExchangeFunction exchangeFunction = request -> {
            MockClientHttpRequest mockRequest = new MockClientHttpRequest(request.method(), request.url());
            request.body().insert(mockRequest, new BodyInserter.Context() {
                @Override
                public List<HttpMessageWriter<?>> messageWriters() {
                    return strategies.messageWriters();
                }

                @Override
                public Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest() {
                    return Optional.empty();
                }

                @Override
                public Map<String, Object> hints() {
                    return Map.of();
                }
            }).block();

            bodyRef.set(mockRequest.getBodyAsString().block());

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
        engine.transcribe(new TranscriptionEngine.Request(UUID.randomUUID(), "obj", "en"));

        String body = bodyRef.get();
        assertThat(body)
                .contains("name=\"response_format\"")
                .contains("diarized_json")
                .contains("name=\"chunking_strategy\"")
                .contains("auto");
        assertThat(body).doesNotContain("timestamp_granularities");
    }

    @Test
    void nonDiarizeRequestsKeepVerboseJsonWithWords() throws Exception {
        StorageService storage = Mockito.mock(StorageService.class);
        Mockito.when(storage.resolveRaw("obj")).thenReturn(tempFile);

        OpenAIAudioProperties props = new OpenAIAudioProperties();
        props.setDiarize(false);
        props.setModel("whisper-1");

        ExchangeStrategies strategies = ExchangeStrategies.withDefaults();
        AtomicReference<String> bodyRef = new AtomicReference<>();

        ExchangeFunction exchangeFunction = request -> {
            MockClientHttpRequest mockRequest = new MockClientHttpRequest(request.method(), request.url());
            request.body().insert(mockRequest, new BodyInserter.Context() {
                @Override
                public List<HttpMessageWriter<?>> messageWriters() {
                    return strategies.messageWriters();
                }

                @Override
                public Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest() {
                    return Optional.empty();
                }

                @Override
                public Map<String, Object> hints() {
                    return Map.of();
                }
            }).block();

            bodyRef.set(mockRequest.getBodyAsString().block());

            ClientResponse response = ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"text\":\"ok\",\"language\":\"en\",\"words\":[]}")
                    .build();
            return Mono.just(response);
        };

        WebClient client = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();

        TranscriptionEngine engine = new OpenAITranscriptionEngine(storage, client, props);
        engine.transcribe(new TranscriptionEngine.Request(UUID.randomUUID(), "obj", "en"));

        String body = bodyRef.get();
        assertThat(body).contains("name=\"response_format\"").contains("verbose_json");
        assertThat(body).contains("timestamp_granularities[]\"\r\n\r\nword");
    }

    @Test
    void parsesDiarizedSegmentsIntoMetaAndText() throws Exception {
        StorageService storage = Mockito.mock(StorageService.class);
        Mockito.when(storage.resolveRaw("obj")).thenReturn(tempFile);

        OpenAIAudioProperties props = new OpenAIAudioProperties();
        props.setDiarize(true);

        ExchangeFunction exchangeFunction = request -> {
            ClientResponse response = ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"segments\":[{\"speaker\":\"A\",\"text\":\"Hello there\",\"start\":0.0,\"end\":1.0},{\"speaker\":\"B\",\"text\":\"Hi again\",\"start\":1.0,\"end\":2.0}]}")
                    .build();
            return Mono.just(response);
        };

        WebClient client = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();

        TranscriptionEngine engine = new OpenAITranscriptionEngine(storage, client, props);
        TranscriptionEngine.Result result = engine.transcribe(new TranscriptionEngine.Request(UUID.randomUUID(), "obj", "en"));

        assertThat(result.text()).isEqualTo("Hello there Hi again");
        assertThat(result.words()).hasSizeGreaterThan(2);
        assertThat(result.words())
                .extracting(TranscriptionEngine.Word::startMs)
                .isSorted();
        assertThat(result.meta()).containsEntry("provider", "openai");
        assertThat((List<?>) result.meta().get("segments")).hasSize(2);
    }

    @Test
    void skipsEmptySegmentsAndLogsWarningsWhileGeneratingWords() throws Exception {
        StorageService storage = Mockito.mock(StorageService.class);
        Mockito.when(storage.resolveRaw("obj")).thenReturn(tempFile);

        OpenAIAudioProperties props = new OpenAIAudioProperties();
        props.setDiarize(true);

        ExchangeFunction exchangeFunction = request -> {
            ClientResponse response = ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"segments\":[{\"speaker\":\"A\",\"text\":\"\",\"start\":0.0,\"end\":1.0},{\"speaker\":\"B\",\"text\":\"Hello world\",\"start\":1.0,\"end\":3.0}]}")
                    .build();
            return Mono.just(response);
        };

        WebClient client = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();

        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(OpenAITranscriptionEngine.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        try {
            TranscriptionEngine engine = new OpenAITranscriptionEngine(storage, client, props);
            TranscriptionEngine.Result result = engine.transcribe(new TranscriptionEngine.Request(UUID.randomUUID(), "obj", "en"));

            assertThat(result.words()).isNotEmpty();
            assertThat(result.words())
                    .extracting(TranscriptionEngine.Word::startMs)
                    .isSorted();

            List<String> warnMessages = listAppender.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            assertThat(warnMessages).anyMatch(msg -> msg.contains("segment missing text"));
        } finally {
            logger.detachAppender(listAppender);
            listAppender.stop();
        }
    }

    @Test
    void parsesAlternativeDiarizedFieldsIntoWords() throws Exception {
        StorageService storage = Mockito.mock(StorageService.class);
        Mockito.when(storage.resolveRaw("obj")).thenReturn(tempFile);

        OpenAIAudioProperties props = new OpenAIAudioProperties();
        props.setDiarize(true);
        props.setLogDiarizeResponse(true);

        ExchangeFunction exchangeFunction = request -> {
            ClientResponse response = ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"utterances\":[{\"speaker\":\"spk1\",\"transcript\":\"Hello world\",\"start_time\":0,\"end_time\":12000},{\"speaker\":\"spk2\",\"utterance\":\"Second part here\",\"start_time\":15000,\"end_time\":30000}]}")
                    .build();
            return Mono.just(response);
        };

        WebClient client = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();

        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(OpenAITranscriptionEngine.class);
        Level previous = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        try {
            TranscriptionEngine engine = new OpenAITranscriptionEngine(storage, client, props);
            TranscriptionEngine.Result result = engine.transcribe(new TranscriptionEngine.Request(UUID.randomUUID(), "obj", "en"));

            assertThat(result.words()).isNotEmpty();
            assertThat(result.words())
                    .extracting(TranscriptionEngine.Word::startMs)
                    .isSorted();
            assertThat(result.meta()).containsKey("segments");

            List<String> debugMessages = listAppender.list.stream()
                    .filter(event -> event.getLevel() == Level.DEBUG)
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            assertThat(debugMessages).anyMatch(msg -> msg.contains("raw response"));
            assertThat(debugMessages).anyMatch(msg -> msg.contains("parsed root"));
        } finally {
            logger.setLevel(previous);
            logger.detachAppender(listAppender);
            listAppender.stop();
        }
    }
}
