package com.example.clipbot_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;


@Service
public class AudioWindowService {
    private static final Logger log = LoggerFactory.getLogger(AudioWindowService.class);
    private final String ffmpeg; // injecteer via @Value of ctor

    public AudioWindowService(@Value("${ffmpeg.bin:ffmpeg}") String ffmpeg) {
        this.ffmpeg = (ffmpeg == null || ffmpeg.isBlank()) ? "ffmpeg" : ffmpeg;
    }
    public Window sliceToTempWav(Path src, long startMs, long endMs, long padMs) {
        long safeStart = Math.max(0, startMs - padMs);
        long safeEnd   = Math.max(safeStart + 1, endMs + padMs);
        long duration  = safeEnd - safeStart;

        try {
            Path out = Files.createTempFile("clipbot-win-", ".wav");
            Process p = new ProcessBuilder(
                    ffmpeg,
                    "-hide_banner", "-loglevel", "error",
                    "-ss", String.format(Locale.ROOT, "%.3f", safeStart / 1000.0),
                    "-t",  String.format(Locale.ROOT, "%.3f", duration  / 1000.0),
                    "-i",  src.toAbsolutePath().toString(),
                    "-ac", "1", "-ar", "16000",
                    "-y",  out.toAbsolutePath().toString()
            )
                    .redirectErrorStream(true) // <- merge stderr->stdout
                    .start();

            // drain stdout zodat buffers niet vollopen
            try (var br = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                while (br.readLine() != null) { /* /dev/null */ }
            }

            boolean finished = p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                throw new RuntimeException("ffmpeg timed out");
            }
            int code = p.exitValue();
            if (code != 0) throw new RuntimeException("ffmpeg failed with code " + code);

            if (!Files.exists(out) || Files.size(out) == 0)
                throw new IOException("ffmpeg produced empty output");

            log.debug("sliceToTempWav: {} → {} ({} ms)", src.getFileName(), out.getFileName(), duration);
            return new Window(out, safeStart);
        } catch (Exception e) {
            throw new RuntimeException("sliceToTempWav failed", e);
        }
    }

    public record Window(Path file, long offsetMs) {}
}

