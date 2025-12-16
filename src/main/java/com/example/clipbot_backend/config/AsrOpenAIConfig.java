package com.example.clipbot_backend.config;

import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.engine.OpenAITranscriptionEngine;
import com.example.clipbot_backend.service.Interfaces.StorageService;

import io.netty.resolver.DefaultAddressResolverGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;


@Configuration
@EnableConfigurationProperties(OpenAIAudioProperties.class)
public class AsrOpenAIConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsrOpenAIConfig.class);
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final Duration RESPONSE_TIMEOUT = Duration.ofMinutes(45);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(45);
    private static final Duration WRITE_TIMEOUT = Duration.ofMinutes(45);
    private static final int MAX_CONNECTIONS = 10;
    private static final Duration PENDING_ACQUIRE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration MAX_IDLE_TIME = Duration.ofSeconds(20);
    private static final Duration MAX_LIFE_TIME = Duration.ofMinutes(5);

    @Bean("openAiWebClient")
    WebClient openAiWebClient(OpenAIAudioProperties props){
        ConnectionProvider provider = ConnectionProvider.builder("openai-http")
                .maxConnections(MAX_CONNECTIONS)
                .pendingAcquireTimeout(PENDING_ACQUIRE_TIMEOUT)
                .maxIdleTime(MAX_IDLE_TIME)
                .maxLifeTime(MAX_LIFE_TIME)
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                // ✅ forceer HTTP/1.1 (vermijdt bad_record_mac issues)
                .protocol(HttpProtocol.HTTP11)
                .compress(false)
                .responseTimeout(RESPONSE_TIMEOUT)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .resolver(DefaultAddressResolverGroup.INSTANCE)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(READ_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(WRITE_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                );

        LOGGER.info(
                "Configuring OpenAI WebClient timeouts connect={}ms response={}s read={}s write={}s maxConn={} pendingAcquire={}s maxIdle={}s maxLife={}s",
                CONNECT_TIMEOUT_MILLIS,
                RESPONSE_TIMEOUT.toSeconds(),
                READ_TIMEOUT.toSeconds(),
                WRITE_TIMEOUT.toSeconds(),
                MAX_CONNECTIONS,
                PENDING_ACQUIRE_TIMEOUT.toSeconds(),
                MAX_IDLE_TIME.toSeconds(),
                MAX_LIFE_TIME.toSeconds()
        );

        return WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Authorization", "Bearer " + props.getApiKey().trim())
                .defaultHeader("Accept", "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                .build();
    }
    @Bean
    TranscriptionEngine transcriptionEngine(
            OpenAIAudioProperties props,
            StorageService storageService,
            @Qualifier("openAiWebClient") WebClient openAiWebClient // ← expliciet deze client
    ) {
        return new OpenAITranscriptionEngine(storageService,openAiWebClient,props);
    }

}
