package com.example.clipbot_backend.config;

import com.example.clipbot_backend.service.LocalStorageService;
import com.example.clipbot_backend.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@EnableConfigurationProperties(StorageProperties.class)
@Configuration
public class StorageConfig {

    @Bean
    public StorageService storageService(StorageProperties properties) {
        Path base = Path.of(properties.getBaseDir());
        return new LocalStorageService(base, properties.getRawPrefix(), properties.getOutPrefix());
    }
}
