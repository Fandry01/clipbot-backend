package com.example.clipbot_backend.service;

import com.example.clipbot_backend.service.Interfaces.StorageService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class UrlDownloader {
    private final StorageService storage;
    private final String ytdlp;

    public UrlDownloader(StorageService storage,
                         @Value("${downloader.ytdlp.bin:yt-dlp}") String ytdlp) {
        this.storage = storage;
        this.ytdlp = ytdlp;
    }

    /** Downloadt naar RAW exact op objectKey en retourneert dat pad */
    public Path ensureRawObject(String url, String objectKey) {
        Path target = storage.resolveRaw(objectKey); // bv data/raw/ext/yt/ID/source.m4a
        try {
            Files.createDirectories(target.getParent());
            // Als het al bestaat: meteen terug
            if (Files.exists(target)) return target;

            Process p = new ProcessBuilder(
                    ytdlp, "-x", "--audio-format", "m4a",
                    "--no-playlist",
                    "-o", target.toString(),  // << exact output pad
                    url
            )
                    .redirectErrorStream(true)
                    .start();

            // (optioneel) lees logs voor debug:
            try (var in = p.getInputStream()) { in.transferTo(System.out); }
            int code = p.waitFor();
            if (code != 0 || !Files.exists(target)) {
                throw new IllegalStateException("yt-dlp exit=" + code + " output missing: " + target);
            }
            return target;
        } catch (Exception e) {
            throw new IllegalStateException("Download failed for " + url + ": " + e.getMessage(), e);
        }
    }
}
