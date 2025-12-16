package com.example.clipbot_backend.service;

import com.example.clipbot_backend.service.Interfaces.StorageService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

@Component
public class UrlDownloader {
    private final StorageService storage;
    private final String ytdlp
            ;
    // HTTP download settings
    private final Integer httpTimeoutMs;
    private final String httpUserAgent;
    private final Integer maxRedirects;
    private final String ffmpegBin;

    public UrlDownloader(StorageService storage,
                         @Value("${downloader.ytdlp.bin:yt-dlp}") String ytdlp,
                         @Value("${downloader.http.timeoutSeconds:120}")Integer httpTimeoutMs,
                         @Value("${downloader.http.userAgent:Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Safari}")String httpUserAgent,
                         @Value("${downloader.http.maxRedirects:5}") Integer maxRedirects,
                         @Value("${ffmpeg.binary:ffmpeg}") String ffmpegBin)
    {
        this.storage = storage;
        this.ytdlp = ytdlp;
        this.httpTimeoutMs = httpTimeoutMs * 1000;
        this.httpUserAgent = httpUserAgent;
        this.maxRedirects = maxRedirects;
        this.ffmpegBin = ffmpegBin;
    }

    /** Downloadt naar RAW exact op objectKey en retourneert dat pad */
    public Path ensureRawObject(String url, String objectKey) {
        Path target = storage.resolveRaw(objectKey); // bv data/raw/ext/yt/ID/source.m4a
        try {
            Files.createDirectories(target.getParent());
            // Als het al bestaat: meteen terug
            if (Files.exists(target)) return target;

            if(isYouTube(url)){
                Path mp4 = target.getParent().resolve("source.mp4");

                if(objectKey.toLowerCase(Locale.ROOT).endsWith(".m4a")){
                    if(!Files.exists(mp4)) downloadYoutubeMp4(url,mp4);
                    if(!Files.exists(target)) extractAudioM4a(mp4,target);
                    return target;
                }
                if (objectKey.toLowerCase(Locale.ROOT).endsWith(".mp4")) {
                    downloadYoutubeMp4(url, target);
                    return target;
                }
                downloadYoutubeMp4(url, mp4);
                return mp4;
            } else{
                downloadWithHttp(url, target);
                if (!Files.exists(target)) {
                    throw new IllegalStateException("Download reported success but target is missing: " + target);
                }
                return target;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Download failed for " + url + ": " + e.getMessage(), e);
        }
    }
    private boolean isYouTube(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return false;
            String h = host.toLowerCase(Locale.ROOT);
            return h.contains("youtube.com") || h.endsWith("youtu.be");
        } catch (Exception ignored) {
            return false;
        }
    }
    private void downloadYoutubeMp4(String url, Path mp4Target) throws Exception {
        Files.createDirectories(mp4Target.getParent());
        // Kies h264 + beste audio, ≤1080p; forceer mp4 container.
        ProcessResult result = runProcess(List.of(
                ytdlp,
                "--no-progress", "--newline",
                "-S", "res:1080,codec:h264",
                "-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]",
                "--merge-output-format", "mp4",
                "--no-playlist",
                "-o", mp4Target.toString(),
                url
        ));

        if (result.code() != 0 || !Files.exists(mp4Target)) {
            throw new IllegalStateException("yt-dlp exit=" + result.code() + " output missing: " + mp4Target + " log=" + truncateLog(result.output()));
        }
    }
    private void extractAudioM4a(Path mp4, Path m4a) throws Exception {
        Files.createDirectories(m4a.getParent());
        // geef tmp-bestand óók een .m4a-extensie
        Path tmp = m4a.resolveSibling(m4a.getFileName().toString().replace(".m4a", "") + ".tmp.m4a");

        // eerst proberen zonder re-encode (snelste) – audio is al AAC
        List<String> cmd = List.of(
                ffmpegBin, "-y",
                "-i", mp4.toAbsolutePath().toString(),
                "-vn",
                "-map", "0:a:0?",
                "-c:a", "copy",
                "-movflags", "+faststart",
                "-f", "ipod",                // forceer container die .m4a begrijpt
                tmp.toAbsolutePath().toString()
        );
        int code = runFF(cmd);
        if (code != 0 || !Files.exists(tmp)) {
            // fallback met re-encode
            List<String> re = List.of(
                    ffmpegBin, "-y",
                    "-i", mp4.toAbsolutePath().toString(),
                    "-vn",
                    "-map", "0:a:0?",
                    "-c:a", "aac", "-b:a", "192k",
                    "-movflags", "+faststart",
                    "-f", "ipod",
                    tmp.toAbsolutePath().toString()
            );
            int code2 = runFF(re);
            if (code2 != 0 || !Files.exists(tmp)) {
                throw new IllegalStateException("ffmpeg extract m4a failed (copy+encode)");
            }
        }

        Files.move(tmp, m4a, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }

    private int runFF(List<String> cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        try (var in = p.getInputStream()) { in.transferTo(System.out); }
        return p.waitFor();
    }

    protected ProcessResult runProcess(List<String> cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        StringJoiner joiner = new StringJoiner(System.lineSeparator());
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                joiner.add(line);
            }
        }
        int code = p.waitFor();
        return new ProcessResult(code, joiner.toString());
    }

    private String truncateLog(String output) {
        final int max = 800;
        if (output == null) {
            return "<no output>";
        }
        if (output.length() <= max) {
            return output;
        }
        return output.substring(0, max) + "...";
    }

    protected record ProcessResult(int code, String output) { }


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
