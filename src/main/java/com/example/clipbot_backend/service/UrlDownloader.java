package com.example.clipbot_backend.service;

import com.example.clipbot_backend.service.Interfaces.StorageService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Component
public class UrlDownloader {
    private final StorageService storage;
    private final String ytdlp
            ;
    // HTTP download settings
    private final Integer httpTimeoutMs;
    private final String httpUserAgent;
    private final Integer maxRedirects;

    public UrlDownloader(StorageService storage,
                         @Value("${downloader.ytdlp.bin:yt-dlp}") String ytdlp, @Value("${downloader.http.timeoutSeconds:120}")Integer httpTimeoutMs,
                         @Value("${downloader.http.userAgent:Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Safari}")String httpUserAgent,
                         @Value("${downloader.http.maxRedirects:5}") Integer maxRedirects) {
        this.storage = storage;
        this.ytdlp = ytdlp;
        this.httpTimeoutMs = httpTimeoutMs;
        this.httpUserAgent = httpUserAgent;
        this.maxRedirects = maxRedirects;
    }

    /** Downloadt naar RAW exact op objectKey en retourneert dat pad */
    public Path ensureRawObject(String url, String objectKey) {
        Path target = storage.resolveRaw(objectKey); // bv data/raw/ext/yt/ID/source.m4a
        try {
            Files.createDirectories(target.getParent());
            // Als het al bestaat: meteen terug
            if (Files.exists(target)) return target;

            if(isYouTube(url)){
                downloadWithYtDlp(url, target);
            } else{
                downloadWithHttp(url, target);
            }

            if (!Files.exists(target)) {
                throw new IllegalStateException("Download reported success but target is missing: " + target);
            }
            return target;
        } catch (Exception e) {
            throw new IllegalStateException("Download failed for " + url + ": " + e.getMessage(), e);
        }
    }
    private boolean isYouTube(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return false;
            String h = host.toLowerCase(Locale.ROOT);
            return h.contains("youtube") || "youtu.be".equals(h);
        } catch (Exception ignored) {
            return false;
        }
    }
    private void downloadWithYtDlp(String url, Path target) throws Exception {
        // Houd je bestaande strategie aan: audio extract naar m4a
        Process p = new ProcessBuilder(
                ytdlp,
                "-x", "--audio-format", "m4a",
                "--no-playlist",
                "-o", target.toString(),
                url
        )
                .redirectErrorStream(true)
                .start();

        try (var in = p.getInputStream()) { in.transferTo(System.out); }
        int code = p.waitFor();
        if (code != 0 || !Files.exists(target)) {
            throw new IllegalStateException("yt-dlp exit=" + code + " output missing: " + target);
        }
    }

    private void downloadWithHttp(String url, Path target) throws Exception {
        // Volg redirects handmatig (HttpURLConnection volgt meestal zelf, maar
        // expliciet houden geeft meer controle en foutmeldingen).
        String current = url;
        int redirects = 0;

        while (redirects <= maxRedirects) {
            HttpURLConnection conn = (HttpURLConnection) URI.create(current).toURL().openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(httpTimeoutMs);
            conn.setReadTimeout(httpTimeoutMs);
            conn.setRequestProperty("User-Agent", httpUserAgent);
            conn.setRequestProperty("Accept", "*/*");

            int status = conn.getResponseCode();
            if (isRedirect(status)) {
                String loc = conn.getHeaderField("Location");
                if (loc == null || loc.isBlank()) {
                    throw new IllegalStateException("Redirect without Location header from: " + current);
                }
                // Relative redirect? resolve tegen current
                current = URI.create(current).resolve(loc).toString();
                redirects++;
                conn.disconnect();
                continue;
            }

            if (status >= 200 && status < 300) {
                // stream naar temp → move (atomic)
                Path tmp = target.resolveSibling(target.getFileName().toString() + ".part");
                try (InputStream is = conn.getInputStream()) {
                    Files.createDirectories(tmp.getParent());
                    Files.copy(is, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    conn.disconnect();
                }
                Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                return;
            }
            try (InputStream es = conn.getErrorStream()) {
                String err = es != null ? new String(es.readAllBytes()) : "<no body>";
                conn.disconnect();
                throw new IllegalStateException("HTTP download failed " + status + " for " + current + " body=" + err);
            }
        }

        throw new IllegalStateException("Too many redirects (" + maxRedirects + ") for " + url);
    }

    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

}
