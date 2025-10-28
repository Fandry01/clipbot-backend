package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.dto.RenderOptions;
import com.example.clipbot_backend.dto.RenderResult;
import com.example.clipbot_backend.dto.RenderSpec;
import com.example.clipbot_backend.dto.SubtitleFiles;
import com.example.clipbot_backend.engine.Interfaces.ClipRenderEngine;

import com.example.clipbot_backend.service.Interfaces.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FfmpegClipRenderEngine  implements ClipRenderEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(FfmpegClipRenderEngine.class);

    private final StorageService storageService;
    private final String ffmpegBin;
    private final Path workDir;
    private final Duration timeout;

    public FfmpegClipRenderEngine(StorageService storageService, String ffmpegBin, Path workDir, Duration timeout) {
        this.storageService = storageService;
        this.ffmpegBin = ffmpegBin;
        this.workDir = workDir.toAbsolutePath().normalize();
        this.timeout = timeout !=  null ? timeout : Duration.ofMinutes(2);
        try {
            Files.createDirectories(this.workDir);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create work directory: " + this.workDir, e);
        }
    }

    @Override
    public RenderResult render(Path inputFile, long startMs, long endMs, RenderOptions options) throws IOException, InterruptedException {
        if (inputFile == null || !Files.exists(inputFile)) {
            throw new IllegalArgumentException("Input file not found: " + inputFile);
        }
        if (startMs < 0 || endMs <= startMs) {
            throw new IllegalArgumentException("Invalid range: startMs=" + startMs + ", endMs=" + endMs);
        }

        // ===== 1) Spec + overrides =====
        RenderSpec spec = options != null ? options.spec() : null;
        if (spec != null && notBlank(spec.profile())) {
            spec = applyProfile(spec);
        }
        Map<String, Object> meta = options != null ? options.meta() : null;

        Integer width  = firstNonNull(asInt(getOrNull(spec, "width")),  asInt(meta, "width"));
        Integer height = firstNonNull(asInt(getOrNull(spec, "height")), asInt(meta, "height"));
        Integer fps    = firstNonNull(asInt(getOrNull(spec, "fps")),    asInt(meta, "fps"));
        Integer crf    = firstNonNull(asInt(getOrNull(spec, "crf")),    asInt(meta, "crf"));
        String  preset = firstNonNull(asStr(getOrNull(spec, "preset")), asStr(meta, "preset"));

        long   durMs     = endMs - startMs;
        double durSec    = durMs / 1000.0;
        // default = midden; mag via meta.thumbnailAt (sec) overschreven worden, maar clamp binnen [0.2s, dur-0.2s]
        Double metaThumb = asDbl(meta, "thumbnailAt"); // seconden, optioneel
        double thumbAtSec = metaThumb != null ? metaThumb : (durSec / 2.0);
        thumbAtSec = Math.max(0.2, Math.min(thumbAtSec, Math.max(0.2, durSec - 0.2)));

        // ===== 2) Bouw ffmpeg (clip) =====
        String outName = "clip-" + UUID.randomUUID() + ".mp4";
        Path   tmpOut  = workDir.resolve(outName);

        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegBin);
        cmd.add("-y");
        cmd.add("-ss"); cmd.add(String.format(java.util.Locale.ROOT, "%.3f", startMs / 1000.0));
        cmd.add("-i");  cmd.add(inputFile.toAbsolutePath().toString());
        cmd.add("-t");  cmd.add(String.format(java.util.Locale.ROOT, "%.3f", durSec));

        // Video
        cmd.add("-c:v"); cmd.add("libx264");
        if (notBlank(preset)) { cmd.add("-preset"); cmd.add(preset); }
        if (crf != null)      { cmd.add("-crf");    cmd.add(String.valueOf(crf)); }
        if (fps != null)      { cmd.add("-r");      cmd.add(String.valueOf(fps)); }

        String vf = null;
        SubtitleFiles subs = options != null ? options.subtitles() : null;
        if (subs != null && notBlank(subs.srtKey())) {
            Path srtPath = resolveFirstExisting(subs.srtKey());
            if (srtPath != null) {
                vf = appendFilter(vf, "subtitles=" + escapeForFilter(srtPath.toAbsolutePath().toString()));
            } else {
                LOGGER.warn("SRT not found for burn-in: {}", subs.srtKey());
            }
        }
        if (width != null && height != null) {
            vf = appendFilter(vf, "scale=" + width + ":" + height);
        }
        if (vf != null) { cmd.add("-vf"); cmd.add(vf); }

        // Audio
        cmd.add("-c:a"); cmd.add("aac");
        cmd.add("-b:a"); cmd.add("128k");

        cmd.add(tmpOut.toAbsolutePath().toString());

        LOGGER.info("FFmpeg command: {}", String.join(" ", cmd));

        // ===== 3) Run ffmpeg (clip) =====
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        Thread logThread = new Thread(() -> {
            try (var br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                br.lines().forEach(line -> LOGGER.debug("[ffmpeg] {}", line));
            } catch (Exception ignore) {}
        });
        logThread.setDaemon(true);
        logThread.start();

        boolean finished = p.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new RuntimeException("ffmpeg timed out after " + timeout);
        }
        if (p.exitValue() != 0) {
            throw new RuntimeException("ffmpeg failed with exit " + p.exitValue());
        }

        // ===== 4) Upload clip =====
        String mp4Key = "clips/" + outName;
        storageService.uploadToOut(tmpOut, mp4Key);
        long mp4Size = Files.size(tmpOut);

        // ===== 5) Thumbnail uit de GERENDERDE clip (correcte moment en met subs/scale) =====
        String thumbKey = null;
        long   thumbSize = 0L;
        try {
            Path thumb = workDir.resolve(outName.replace(".mp4", ".jpg"));
            List<String> tcmd = List.of(
                    ffmpegBin, "-y",
                    "-ss", String.format(java.util.Locale.ROOT, "%.3f", thumbAtSec),
                    "-i", tmpOut.toAbsolutePath().toString(), // ← uit gerenderde clip
                    "-frames:v", "1",
                    "-vf", "scale=640:-1",
                    "-q:v", "3",
                    thumb.toAbsolutePath().toString()
            );
            Process tp = new ProcessBuilder(tcmd).redirectErrorStream(true).start();
            tp.waitFor();

            if (Files.exists(thumb)) {
                thumbKey = "clips/thumbs/" + thumb.getFileName(); // bv. clips/thumbs/clip-xxxx.jpg
                storageService.uploadToOut(thumb, thumbKey);
                thumbSize = Files.size(thumb);
                Files.deleteIfExists(thumb);
            }
        } catch (Exception e) {
            LOGGER.warn("thumbnail generation failed: {}", e.toString());
        } finally {
            // tmpOut pas hier opruimen
            try { Files.deleteIfExists(tmpOut); } catch (Exception ignore) {}
        }

        return new RenderResult(mp4Key, mp4Size, thumbKey, thumbSize);
    }

    private Path resolveFirstExisting(String objectKey) {
        try {
            Path out = storageService.resolveOut(objectKey);
            if (Files.exists(out)) return out;
        } catch (Exception ignored) {}
        try {
            Path raw = storageService.resolveRaw(objectKey);
            if (Files.exists(raw)) return raw;
        } catch (Exception ignored) {}
        return null;
    }
    private RenderSpec applyProfile(RenderSpec spec) {
        RenderSpec defaults = switch (spec.profile()) {
            case "tiktok-9x16" -> new RenderSpec(1080, 1920, 30, 23, "veryfast", "tiktok-9x16");
            case "youtube-1080p" -> new RenderSpec(1920, 1080, 30, 23, "fast", "youtube-1080p");
            default -> null;
        };
        if (defaults == null) return spec;
        return new RenderSpec(
                spec.width()  != null ? spec.width()  : defaults.width(),
                spec.height() != null ? spec.height() : defaults.height(),
                spec.fps()    != null ? spec.fps()    : defaults.fps(),
                spec.crf()    != null ? spec.crf()    : defaults.crf(),
                spec.preset() != null ? spec.preset() : defaults.preset(),
                spec.profile()
        );
    }
    private static <T> T firstNonNull(T a, T b) { return a != null ? a : b; }

    private static Object getOrNull(RenderSpec spec, String field) {
        if (spec == null) return null;
        return switch (field) {
            case "width"  -> spec.width();
            case "height" -> spec.height();
            case "fps"    -> spec.fps();
            case "crf"    -> spec.crf();
            case "preset" -> spec.preset();
            default -> null;
        };
    }
    private static Integer asInt(Map<String, Object> meta, String key) {
        if (meta == null) return null;
        Object v = meta.get(key);
        return asInt(v);
    }
    private static Integer asInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }
    private static String asStr(Map<String, Object> meta, String key) {
        if (meta == null) return null;
        Object v = meta.get(key);
        return asStr(v);
    }
    private static String asStr(Object v) { return v != null ? v.toString() : null; }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static String appendFilter(String current, String add) {
        return current == null ? add : current + "," + add;
    }

    /** Eenvoudige escape voor ffmpeg subtitles filter. */
    private static String escapeForFilter(String path) {
        return path.replace("\\", "\\\\").replace(":", "\\:").replace("'", "\\'");
    }
    @SuppressWarnings("unchecked")
    static Double asDbl(Map<String, Object> meta, String key) {
        if (meta == null) return null;
        Object v = meta.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof CharSequence s) {
            String txt = s.toString().trim();
            if (txt.isEmpty()) return null;
            try { return Double.parseDouble(txt); } catch (NumberFormatException ignore) {}
        }
        return null;
    }

    }
