package com.example.clipbot_backend.config;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class MetadataClientConfig {

    @Bean
    @Qualifier("metadataWebClient")
    public WebClient metadataWebClient(WebClient.Builder builder) {
        HttpClient http = HttpClient.create()
                .followRedirect(true) // ← belangrijk
                .compress(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) Duration.ofSeconds(5).toMillis())
                .responseTimeout(Duration.ofSeconds(10));

        // Grotere in-memory limit voor grote HTMLs / oEmbed responses
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return builder
                .clientConnector(new ReactorClientHttpConnector(http))
                .exchangeStrategies(strategies)
                // Gebruik een "echte" browser UA + taal + accept + referrer
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9,nl;q=0.8")
                .defaultHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .defaultHeader("Referer", "https://www.google.com/")
                .build();
    }
}

