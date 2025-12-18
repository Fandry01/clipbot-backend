package com.example.clipbot_backend.service;

import com.example.clipbot_backend.service.Interfaces.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

@Component
public class UrlDownloader {
    private static final Logger log = LoggerFactory.getLogger(UrlDownloader.class);
    private static final long YTDLP_TIMEOUT_MINUTES = 15;
    private static final int LOG_SNIPPET_MAX = 4_000;
    private final StorageService storage;
    private final String ytdlp;
    private final String ytdlpCookiesFile;
    // HTTP download settings
    private final Integer httpTimeoutMs;
    private final String httpUserAgent;
    private final Integer maxRedirects;
    private final String ffmpegBin;

    public UrlDownloader(StorageService storage,
                         @Value("${downloader.ytdlp.bin:yt-dlp}") String ytdlp,
                         @Value("${downloader.http.timeoutSeconds:120}") Integer httpTimeoutMs,
                         @Value("${downloader.http.userAgent:Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Safari}") String httpUserAgent,
                         @Value("${downloader.http.maxRedirects:5}") Integer maxRedirects,
                         @Value("${ffmpeg.binary:ffmpeg}") String ffmpegBin,
                         @Value("${YTDLP_COOKIES_FILE:#{null}}") String ytdlpCookiesFile) {
        this.storage = storage;
        this.ytdlp = ytdlp;
        this.httpTimeoutMs = httpTimeoutMs * 1000;
        this.httpUserAgent = httpUserAgent;
        this.maxRedirects = maxRedirects;
        this.ffmpegBin = ffmpegBin;
        this.ytdlpCookiesFile = ytdlpCookiesFile;
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
        List<String> cmd = new ArrayList<>(List.of(
                ytdlp,
                "--no-progress", "--newline",
                "-S", "res:1080,codec:h264",
                "-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]",
                "--merge-output-format", "mp4",
                "--no-playlist"
        ));

        maybeAddCookies(cmd);

        cmd.add("-o");
        cmd.add(mp4Target.toString());
        cmd.add(url);

        ProcessResult result = runProcess(cmd, YTDLP_TIMEOUT_MINUTES);

        if (result.timedOut()) {
            String partialNote = cleanupPartial(mp4Target);
            throw new IllegalStateException("yt-dlp timeout after " + YTDLP_TIMEOUT_MINUTES + "m for " + url + partialNote + " log=" + truncateLog(result.output()));
        }

        if (result.code() != 0 || !Files.exists(mp4Target)) {
            String output = result.output();
            String partialNote = cleanupPartial(mp4Target);
            if (isAuthWall(output)) {
                throw new IllegalStateException("YouTube download requires authentication/cookies for " + url + partialNote + " log=" + truncateLog(output));
            }
            throw new IllegalStateException("yt-dlp exit=" + result.code() + " output missing: " + mp4Target + partialNote + " log=" + truncateLog(output));
        }

        log.info("yt-dlp download OK target={} url={}", mp4Target, url);
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

    protected ProcessResult runProcess(List<String> cmd, long timeoutMinutes) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        StringJoiner joiner = new StringJoiner(System.lineSeparator());
        Thread reader = new Thread(() -> {
            try (var buffered = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = buffered.readLine()) != null) {
                    joiner.add(line);
                }
            } catch (IOException ignored) {
                // Process failure is handled by exit code/timeout; output collected so far is enough.
            }
        });
        reader.start();

        boolean finished = p.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        if (!finished) {
            p.destroyForcibly();
            p.waitFor(5, TimeUnit.SECONDS);
        }
        reader.join();
        int code = finished ? p.exitValue() : -1;
        return new ProcessResult(code, joiner.toString(), !finished);
    }

    private String truncateLog(String output) {
        if (output == null || output.isBlank()) {
            return "<no output>";
        }
        if (output.length() <= LOG_SNIPPET_MAX) {
            return output;
        }
        return output.substring(0, LOG_SNIPPET_MAX) + "...";
    }

    private boolean isAuthWall(String output) {
        if (output == null) {
            return false;
        }
        String normalized = output.toLowerCase(Locale.ROOT)
                .replace('\u2019', '\'');
        return normalized.contains("sign in to confirm you're not a bot")
                || normalized.contains("sign in to confirm you\u2019re not a bot")
                || normalized.contains("--cookies-from-browser")
                || normalized.contains("use --cookies");
    }

    private void maybeAddCookies(List<String> cmd) {
        if (ytdlpCookiesFile == null || ytdlpCookiesFile.isBlank()) {
            return;
        }
        Path cookiesPath = Path.of(ytdlpCookiesFile).toAbsolutePath();
        if (Files.exists(cookiesPath)) {
            cmd.add("--cookies");
            cmd.add(cookiesPath.toString());
        } else {
            log.warn("yt-dlp cookies file configured but missing path={}", cookiesPath);
        }
    }

    private String cleanupPartial(Path mp4Target) {
        Path partial = mp4Target.resolveSibling(mp4Target.getFileName().toString() + ".part");
        if (Files.exists(partial)) {
            try {
                Files.deleteIfExists(partial);
            } catch (IOException e) {
                log.warn("Failed to delete yt-dlp partial file partial={}", partial, e);
            }
            return " partial=" + partial;
        }
        return "";
    }

    protected record ProcessResult(int code, String output, boolean timedOut) { }


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
