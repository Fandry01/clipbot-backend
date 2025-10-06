package com.example.clipbot_backend.config;

import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.engine.WhisperApiTranscriptionEngine;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;


@Configuration
@EnableConfigurationProperties(ApiAsrProperties.class)
public class AsrApiConfig {
    @Bean
    @ConditionalOnProperty(name = "engine.asr", havingValue = "api")
    public TranscriptionEngine apiTranscriptionEngine(
            StorageService storageService,
            ApiAsrProperties properties,
            ObjectMapper objectMapper,
            @Value("{engine.asr.workDir:./data/work}") String workDir
    ){
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15_000)
                .responseTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .doOnConnected(connection ->  connection.addHandlerLast(new ReadTimeoutHandler(properties.getTimeoutSeconds(), TimeUnit.SECONDS)));

        ExchangeStrategies strategies = ExchangeStrategies.builder().codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(64*1024*1024))
                .build();

        WebClient apiClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies).defaultHeaders( httpHeaders -> {
                    if(properties.getApiKey() != null && !properties.getApiKey().isBlank()){
                        httpHeaders.setBearerAuth(properties.getApiKey());
                    }
                }).build();

        WebClient rawClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(http))
                .exchangeStrategies(strategies)
                .build();

        return new WhisperApiTranscriptionEngine(storageService, apiClient, rawClient, objectMapper, properties, workDir);
    }
}

