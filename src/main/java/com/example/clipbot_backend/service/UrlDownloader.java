package com.example.clipbot_backend.service;

import com.example.clipbot_backend.service.Interfaces.StorageService;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class UrlDownloader {
    private final StorageService storage;

    public UrlDownloader(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Zorgt dat objectKey in RAW bestaat.
     * Verwacht dat objectKey een pad met bestandsnaam is, bv: ext/yt/<id>/source.mp3
     * @return het Path naar het bestand in RAW
     */
    public Path ensureRawObject(String externalUrl, String objectKey) {
        if (storage.existsInRaw(objectKey)) {
            return storage.resolveRaw(objectKey);
        }
        // Download naar temp
        try {
            Path tmp = Files.createTempFile("dl-", ".bin");

            // Gebruik yt-dlp voor YouTube (en co); als fallback kun je later HTTP GET doen
            // Download audio-only naar tmp (we overwrite tmp)
            ProcessBuilder pb = new ProcessBuilder(
                    "yt-dlp",
                    "-x", "--audio-format", "mp3",
                    "-o", tmp.toAbsolutePath().toString(),   // direct naar tmp
                    externalUrl
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (var r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String line; while ((line = r.readLine()) != null) {
                    // log desnoods
                }
            }
            int code = p.waitFor();
            if (code != 0) throw new IllegalStateException("yt-dlp failed with exit code " + code);

            // Zet naar RAW onder objectKey
            storage.uploadToRaw(tmp, objectKey);
            Files.deleteIfExists(tmp);

            return storage.resolveRaw(objectKey);
        } catch (Exception e) {
            throw new IllegalStateException("Download failed for " + externalUrl + ": " + e.getMessage(), e);
        }
    }
}

