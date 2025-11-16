package com.example.clipbot_backend.config;

import com.example.clipbot_backend.engine.FfmpegClipRenderEngine;
import com.example.clipbot_backend.engine.Interfaces.ClipRenderEngine;
import com.example.clipbot_backend.engine.Interfaces.DetectionEngine;
import com.example.clipbot_backend.engine.DetectionEngineImpl;
import com.example.clipbot_backend.service.Interfaces.SilenceDetector;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import java.nio.file.Path;
import java.time.Duration;

@Configuration
public class EngineConfig {

    @Bean
    public DetectionEngine detectionEngine(SilenceDetector silenceDetector) {
        return new DetectionEngineImpl(silenceDetector);
    }

    @Bean
    public ClipRenderEngine clipRenderEngine(
            StorageService storageService,
            @Value("${ffmpeg.binary:ffmpeg}") String ffmpegBin,
            @Value("${clip.render.workDir:./data/work}") String workDir,
            @Value("${clip.render.timeoutSeconds:180}") long timeoutSeconds,
            @Value("${engine.render.fontsDir:}")Path fontsDir
    ) {
        return new FfmpegClipRenderEngine(
                storageService,
                ffmpegBin,
                Path.of(workDir),
                Duration.ofSeconds(Math.max(1, timeoutSeconds)),
                fontsDir
        );
    }
}

