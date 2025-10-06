package com.example.clipbot_backend.config;

import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.engine.OpenAITranscriptionEngine;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(OpenAIAudioProperties.class)
public class AsrOpenAIConfig {
    @Bean
    @ConditionalOnProperty(name = "engine.asr", havingValue = "openai", matchIfMissing = true)
    public TranscriptionEngine openAITranscriptEngine(
            StorageService storageService,
            OpenAIAudioProperties properties
    ){
        var client = WebClient.builder().baseUrl(properties.getBaseUrl())
                .defaultHeaders(headers -> headers.setBearerAuth(properties.getApiKey()))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(64 * 1024 * 1024))
                .build();
        return new OpenAITranscriptionEngine(storageService, client, properties);
    }
}
