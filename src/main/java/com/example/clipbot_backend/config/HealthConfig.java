
package com.example.clipbot_backend.config;

import org.springframework.boot.actuate.health.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Configuration
public class HealthConfig {

    @Bean
    public HealthIndicator ffmpegHealth() {
        return () -> {
            try {
                var p = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
                if (p.waitFor() == 0) return Health.up().withDetail("ffmpeg", "ok").build();
            } catch (Exception ignored) {}
            return Health.down().withDetail("ffmpeg", "missing").build();
        };
    }

    @Bean
    public HealthIndicator fasterWhisperHealth(
            @org.springframework.beans.factory.annotation.Qualifier("fwWebClient")
                                               WebClient fw) {
        return () -> {
            try {
                // lichte check – HEAD / 200 OK
                fw.head().uri("/")
                        .retrieve()
                        .toBodilessEntity()
                        .block(Duration.ofSeconds(2));
                return Health.up().withDetail("fasterWhisper", "ok").build();
            } catch (Exception e) {
                return Health.down(e).withDetail("fasterWhisper", "unreachable").build();
            }
        };
    }
}
