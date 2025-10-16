package com.example.clipbot_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class TimeConfig {
    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}