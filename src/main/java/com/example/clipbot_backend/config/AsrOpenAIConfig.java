package com.example.clipbot_backend.config;

import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.engine.OpenAITranscriptionEngine;
import com.example.clipbot_backend.service.Interfaces.StorageService;

import io.netty.resolver.DefaultAddressResolverGroup;
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
    @Bean("openAiWebClient")
    WebClient openAiWebClient(OpenAIAudioProperties props){
        ConnectionProvider provider = ConnectionProvider.builder("openai-http")
                .maxConnections(20)
                .pendingAcquireTimeout(Duration.ofSeconds(20))
                .maxIdleTime(Duration.ofSeconds(20))
                .maxLifeTime(Duration.ofMinutes(2))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                // ✅ forceer HTTP/1.1 (vermijdt bad_record_mac issues)
                .protocol(HttpProtocol.HTTP11)
                .compress(true)
                .responseTimeout(Duration.ofSeconds(120))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .resolver(DefaultAddressResolverGroup.INSTANCE)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(120, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(120, TimeUnit.SECONDS))
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
