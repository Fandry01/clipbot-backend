package com.example.clipbot_backend.config;

import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.engine.WhisperLocalTranscriptionEngine;
import com.example.clipbot_backend.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;

@Configuration
public class AsrEngineConfig {

    @Bean
    @ConditionalOnProperty(name = "engine.asr", havingValue = "whisper-local", matchIfMissing = true)
    public TranscriptionEngine whisperLocalEngine(
            StorageService storage,
            @Value("${ffmpeg.binary:ffmpeg}") String ffmpegBin,
            @Value("${asr.whisper.cmd:whisper}") String whisperCmd,
            @Value("${asr.whisper.model:medium}") String whisperModel,
            @Value("${engine.asr.workDir:./data/work}") String workDir,
            @Value("${engine.asr.timeoutSeconds:600}") long timeoutSeconds
    ) {
        return new WhisperLocalTranscriptionEngine(
                storage,
                ffmpegBin,
                whisperCmd,
                whisperModel,
                Duration.ofSeconds(timeoutSeconds),
                Path.of(workDir)
        );
    }
}
