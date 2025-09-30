package com.example.clipbot_backend.config;

import com.example.clipbot_backend.service.LocalStorageService;
import com.example.clipbot_backend.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class StorageConfig {

    @Bean
    public StorageService storageService(
            @Value("${storage.type:local}") String type,
            @Value("{storage.local.baseDir:./data}") String baseDir,
            @Value("${storage.local.rawPrefix:raw}") String rawPrefix,
            @Value("${storage.local.outPrefix:out}") String outPrefix
    ){
        if ("local".equalsIgnoreCase(type)) {
            return new LocalStorageService(Path.of(baseDir), rawPrefix, outPrefix);
        }
        throw new IllegalStateException("Unsupported storage.type=" +type);
    }
}
