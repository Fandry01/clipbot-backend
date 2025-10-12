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
    public WebClient openAIWebClient(OpenAIAudioProperties props) {
        return WebClient.builder()
                .baseUrl(props.getBaseUrl()) // https://api.openai.com
                .defaultHeaders(h -> h.setBearerAuth(props.getApiKey()))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(64 * 1024 * 1024))
                .build();
    }

    @Bean
    public TranscriptionEngine openAITranscriptEngine(StorageService storage,
                                                      WebClient openAIWebClient,
                                                      OpenAIAudioProperties props) {
        return new OpenAITranscriptionEngine(storage, openAIWebClient, props);
    }

}
