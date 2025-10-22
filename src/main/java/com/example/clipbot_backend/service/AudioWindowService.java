package com.example.clipbot_backend.service;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Service
public class AudioWindowService {
    // Snijdt [startMs - pad, endMs + pad] naar een temp WAV en geeft offset terug.
    public Window sliceToTempWav(Path src, long startMs, long endMs, long padMs) {
        long safeStart = Math.max(0, startMs - padMs);
        long safeEnd   = Math.max(safeStart + 1, endMs + padMs);
        long duration  = safeEnd - safeStart;

        try {
            Path out = Files.createTempFile("clipbot-win-", ".wav");
            // Vereist ffmpeg in PATH. Her-encode naar WAV om model-compat te maximaliseren.
            Process p = new ProcessBuilder(
                    "ffmpeg",
                    "-hide_banner", "-loglevel", "error",
                    "-ss", String.format(Locale.ROOT, "%.3f", safeStart / 1000.0),
                    "-t",  String.format(Locale.ROOT, "%.3f", duration / 1000.0),
                    "-i",  src.toAbsolutePath().toString(),
                    "-ac", "1", "-ar", "16000",
                    "-y", out.toAbsolutePath().toString()
            ).start();
            if (p.waitFor() != 0) throw new RuntimeException("ffmpeg failed with code " + p.exitValue());
            return new Window(out, safeStart);
        } catch (Exception e) {
            throw new RuntimeException("sliceToTempWav failed", e);
        }
    }

    public record Window(Path file, long offsetMs) {}
}
