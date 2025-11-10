package com.example.clipbot_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(FwProperties.class)
public class FwClientConfig {

    @Bean("fwWebClient")
    public WebClient fwWebClient(FwProperties props) {
        var to = Duration.ofSeconds(props.getTimeoutSeconds());
        int toSec = (int) Math.max(1, to.getSeconds());

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(32 * 1024 * 1024)) // 16MB i.p.v. default ~256KB
                .build();

        HttpClient http = HttpClient.create()
                .responseTimeout(to)
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 15_000)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new io.netty.handler.timeout.ReadTimeoutHandler((int) to.getSeconds()))
                        .addHandlerLast(new io.netty.handler.timeout.WriteTimeoutHandler((int) to.getSeconds()))
                );

        return WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(http))
                .exchangeStrategies(strategies)
                .build();
    }
}