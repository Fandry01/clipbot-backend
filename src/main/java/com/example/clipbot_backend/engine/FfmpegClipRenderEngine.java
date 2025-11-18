package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.dto.RenderOptions;
import com.example.clipbot_backend.dto.RenderResult;
import com.example.clipbot_backend.dto.RenderSpec;
import com.example.clipbot_backend.dto.SubtitleFiles;
import com.example.clipbot_backend.engine.Interfaces.ClipRenderEngine;

import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.example.clipbot_backend.util.SmartThumbnailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

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

    private static final int FONT_MIN_PX = 14;
    private static final int FONT_MAX_PX = 36;
    private static final int MIN_MARGIN_V_PX = 44;
    private static final int MIN_MARGIN_H_PX = 120;
    private static final double OUTLINE_RATIO = 0.08;
    private static final double AR_THRESHOLD = 1.3;
    private static final double DEFAULT_WIDE_FONT_MUL = 0.0200;
    private static final double DEFAULT_TALL_FONT_MUL = 0.0230;
    private static final double DEFAULT_WIDE_MARGIN_MUL = 0.18;
    private static final double DEFAULT_TALL_MARGIN_MUL = 0.15;

    private final StorageService storageService;
    private final String ffmpegBin;
    private final Path workDir;
    private final Duration timeout;
    private final @Nullable Path fontsDir;

    public FfmpegClipRenderEngine(StorageService storageService, String ffmpegBin, Path workDir, Duration timeout, @Nullable Path fontsDir) {
        this.storageService = storageService;
        this.ffmpegBin = ffmpegBin;
        this.workDir = workDir.toAbsolutePath().normalize();
        this.timeout = timeout !=  null ? timeout : Duration.ofMinutes(2);
        this.fontsDir = fontsDir != null ? fontsDir.toAbsolutePath().normalize() : null;
        try {
            Files.createDirectories(this.workDir);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create work directory: " + this.workDir, e);
        }
    }
    private static boolean probablyAudioOnly(Path file) {
        try {
            String ct = java.nio.file.Files.probeContentType(file);
            if (ct != null && ct.startsWith("audio/")) return true;
        } catch (Exception ignore) {}
        // simpele extensie-check als fallback
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".mp3") || name.endsWith(".wav");
    }
    private String subtitleStyleForHeight(int videoH, int videoW,@Nullable Map<String,Object> meta) {
        // Dynamisch per aspect ratio
        double ar = (videoW > 0) ? (videoW * 1.0 / Math.max(1, videoH)) : 16.0/9.0;
        double mul = (ar >= AR_THRESHOLD) ? DEFAULT_WIDE_FONT_MUL : DEFAULT_TALL_FONT_MUL;
        Double sc = asDbl(meta, "subtitleScale");
        if (sc != null) {
            // guardrails
            mul = Math.max(0.014, Math.min(0.030, sc));
        }
        int fontPx   = Math.max(FONT_MIN_PX, Math.min(FONT_MAX_PX, (int)Math.round(videoH * mul))); // ~22px @1080p
        int outline = Math.max(1, Math.min(2, (int)Math.round(fontPx * OUTLINE_RATIO)));  // dunne rand
        int marginV = Math.max(MIN_MARGIN_V_PX, (int)Math.round(videoH * 0.006)); // ~32px @1080p

        double marginHMul = ar >= AR_THRESHOLD ? DEFAULT_WIDE_MARGIN_MUL : DEFAULT_TALL_MARGIN_MUL; // bredere schermen → smallere textblock breedte
        int marginH = Math.max(MIN_MARGIN_H_PX, (int)Math.round(videoW * marginHMul)); // grotere marge voor meer regelafbreking

        return "FontName=Inter Semi Bold"
                + ",FontSize=" + fontPx
                + ",PrimaryColour=&H00FFFFFF"
                + ",OutlineColour=&H80000000"
                + ",BackColour=&H80000000"
                + ",BorderStyle=3"   // boxed
                + ",Outline=" + outline
                + ",Shadow=0"
                + ",Spacing=0"
                + ",MarginL=" + marginH + ",MarginR=" + marginH + ",MarginV=" + marginV
                + ",Alignment=2"
                + ",WrapStyle=2"; // nette regelafbreking
    }

    private static int orDefault(Integer v, int def) { return v != null ? v : def; }

    @Override
    public RenderResult render(Path inputFile, long startMs, long endMs, RenderOptions options)
            throws IOException, InterruptedException {

        if (inputFile == null || !Files.exists(inputFile)) {
            throw new IllegalArgumentException("Input file not found: " + inputFile);
        }
        if (startMs < 0 || endMs <= startMs) {
            throw new IllegalArgumentException("Invalid range: startMs=" + startMs + ", endMs=" + endMs);
        }

        // ----- spec / overrides -----
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

        long   durMs   = endMs - startMs;
        double durSec  = durMs / 1000.0;

        Double metaThumb = asDbl(meta, "thumbnailAt");
        double thumbAtSec = metaThumb != null ? metaThumb : (durSec / 2.0);
        thumbAtSec = Math.max(0.2, Math.min(thumbAtSec, Math.max(0.2, durSec - 0.2)));

        // ----- canvas/defaults -----
        boolean audioOnly = probablyAudioOnly(inputFile);
        int W = orDefault(width, 1920);  if ((W & 1) == 1) W++;
        int H = orDefault(height, 1080); if ((H & 1) == 1) H++;
        int FPS = orDefault(fps, 30);
        int targetCrf = orDefault(crf, 18);
        String targetPreset = (preset != null && !preset.isBlank()) ? preset : "medium";

        String outName = "clip-" + UUID.randomUUID() + ".mp4";
        Path   tmpOut  = workDir.resolve(outName);

        // ----- ffmpeg command -----
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegBin);
        cmd.add("-y");

        SubtitleFiles subs = options != null ? options.subtitles() : null;
        String vf = null;

        if (!audioOnly) {
            // ========== VIDEO BRON ==========
            cmd.add("-ss"); cmd.add(String.format("%.3f", startMs / 1000.0));
            cmd.add("-i");  cmd.add(inputFile.toAbsolutePath().toString());

            // 1) scale/pad naar target
            if (width != null && height != null) {
                vf = appendFilter(vf, "scale=" + width + ":" + height + ":force_original_aspect_ratio=decrease");
                vf = appendFilter(vf, "pad=" + width + ":" + height + ":(ow-iw)/2:(oh-ih)/2");
            }

            // 2) subtitles (na scale/pad, zodat FontSize klopt op outputresolutie)
            if (subs != null && notBlank(subs.srtKey())) {
                Path srtPath = resolveFirstExisting(subs.srtKey());
                if (srtPath != null && Files.exists(srtPath)) {
                    String srtEsc = escapeForFilter(srtPath.toAbsolutePath().toString());
                    String style  = subtitleStyleForHeight(H,W,meta).replace("'", "\\'");
                    String subFilter = "subtitles='" + srtEsc + "':force_style='" + style + "'";
                    if (fontsDir != null) subFilter += ":fontsdir='" + escapeForFilter(fontsDir.toString()) + "'";
                    vf = appendFilter(vf, subFilter);
                } else {
                    LOGGER.warn("SRT not found for burn-in (skipping): {}", subs.srtKey());
                }
            }
            if (vf != null) { cmd.add("-vf"); cmd.add(vf); }

            // encoders
            cmd.add("-c:v"); cmd.add("libx264");
            cmd.add("-preset"); cmd.add(targetPreset);
            cmd.add("-crf"); cmd.add(String.valueOf(targetCrf));
            if (fps != null) { cmd.add("-r"); cmd.add(String.valueOf(FPS)); } // optioneel
            cmd.add("-c:a"); cmd.add("aac");
            cmd.add("-b:a"); cmd.add("128k");

        } else {
            // ========== AUDIO-ONLY → ZWARTE CANVAS + AUDIO ==========
            cmd.add("-f"); cmd.add("lavfi");
            cmd.add("-i"); cmd.add("color=color=black:size=" + W + "x" + H + ":rate=" + FPS); // input #0 = canvas

            cmd.add("-ss"); cmd.add(String.format("%.3f", startMs / 1000.0));
            cmd.add("-i");  cmd.add(inputFile.toAbsolutePath().toString());                     // input #1 = audio

            // 1) scale/pad (veilig, canvas is al juist maar uniform houden)
            vf = appendFilter(vf, "scale=" + W + ":" + H + ":force_original_aspect_ratio=decrease");
            vf = appendFilter(vf, "pad=" + W + ":" + H + ":(ow-iw)/2:(oh-ih)/2");

            // 2) subtitles op canvas
            if (subs != null && notBlank(subs.srtKey())) {
                Path srtPath = resolveFirstExisting(subs.srtKey());
                if (srtPath != null && Files.exists(srtPath)) {
                    String srtEsc = escapeForFilter(srtPath.toAbsolutePath().toString());
                    String style  = subtitleStyleForHeight(H,W,meta).replace("'", "\\'");
                    String subFilter = "subtitles='" + srtEsc + "':force_style='" + style + "'";
                    if (fontsDir != null) subFilter += ":fontsdir='" + escapeForFilter(fontsDir.toString()) + "'";
                    vf = appendFilter(vf, subFilter);
                } else {
                    LOGGER.warn("SRT not found for burn-in (skipping): {}", subs.srtKey());
                }
            }
            if (vf != null) { cmd.add("-vf"); cmd.add(vf); }

            // mapping + encoders
            cmd.add("-map"); cmd.add("0:v:0");  // video: canvas
            cmd.add("-map"); cmd.add("1:a:0?"); // audio: bron (optioneel)
            cmd.add("-c:v"); cmd.add("libx264");
            cmd.add("-preset"); cmd.add(targetPreset);
            cmd.add("-crf"); cmd.add(String.valueOf(targetCrf));
            cmd.add("-c:a"); cmd.add("aac");
            cmd.add("-b:a"); cmd.add("128k");
            cmd.add("-shortest");
        }

        // universele flags
        cmd.add("-t");        cmd.add(String.format("%.3f", durMs / 1000.0));
        cmd.add("-pix_fmt");  cmd.add("yuv420p");
        cmd.add("-movflags"); cmd.add("+faststart");
        cmd.add("-force_key_frames"); cmd.add("expr:gte(t,0)");
        cmd.add(tmpOut.toAbsolutePath().toString());

        LOGGER.info("FFmpeg command: {}", String.join(" ", cmd));

        // ----- run ffmpeg -----
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(false);
        Process p = pb.start();

        StringBuilder outBuf = new StringBuilder();
        StringBuilder errBuf = new StringBuilder();

        Thread tOut = new Thread(() -> {
            try (var br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                br.lines().forEach(line -> { LOGGER.debug("[ffmpeg-out] {}", line); outBuf.append(line).append('\n'); });
            } catch (Exception ignore) {}
        });
        Thread tErr = new Thread(() -> {
            try (var br = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                br.lines().forEach(line -> { LOGGER.debug("[ffmpeg-err] {}", line); errBuf.append(line).append('\n'); });
            } catch (Exception ignore) {}
        });
        tOut.setDaemon(true); tErr.setDaemon(true);
        tOut.start(); tErr.start();

        boolean finished = p.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new RuntimeException("ffmpeg timed out after " + timeout + "\n---- ffmpeg stderr ----\n" + errBuf);
        }
        if (p.exitValue() != 0) {
            throw new RuntimeException("ffmpeg failed with exit " + p.exitValue()
                    + "\n---- ffmpeg stderr ----\n" + errBuf + "\n---- ffmpeg stdout ----\n" + outBuf);
        }

        // ----- upload clip -----
        String mp4Key = "clips/" + outName;
        storageService.uploadToOut(tmpOut, mp4Key);
        long mp4Size = Files.size(tmpOut);

        // ----- thumbnails -----
        String thumbKey = null;
        long   thumbSize = 0L;
        try {
            Path   thumbSrc   = audioOnly ? tmpOut : inputFile; // video: origineel zonder gebrande subs, audio-only: de render
            double thumbStart = audioOnly ? 0.0 : startMs / 1000.0;
            double thumbEnd   = audioOnly ? durMs / 1000.0 : endMs / 1000.0;

            SmartThumbnailer tn = new SmartThumbnailer(ffmpegBin, workDir);
            Path best = tn.generate(thumbSrc, thumbStart, thumbEnd, W, H);


            thumbKey  = "clips/thumbs/" + best.getFileName();
            storageService.uploadToOut(best, thumbKey);
            thumbSize = Files.size(best);
            Files.deleteIfExists(best);
        } catch (Exception e) {
            LOGGER.warn("smart thumbnail failed, falling back to mid-frame: {}", e.toString());
            Path thumb = workDir.resolve(outName.replace(".mp4", ".jpg"));
            List<String> tcmd = List.of(
                    ffmpegBin, "-y",
                    "-ss", String.format(java.util.Locale.ROOT, "%.3f", Math.max(0.2, (durMs/2000.0) - 0.1)),
                    "-i",  tmpOut.toAbsolutePath().toString(),
                    "-vframes", "1",
                    "-q:v", "3",
                    thumb.toAbsolutePath().toString()
            );
            new ProcessBuilder(tcmd).redirectErrorStream(true).start().waitFor();
            if (Files.exists(thumb)) {
                thumbKey  = "clips/thumbs/" + thumb.getFileName();
                storageService.uploadToOut(thumb, thumbKey);
                thumbSize = Files.size(thumb);
                Files.deleteIfExists(thumb);
            }
        } finally {
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
        return path
                .replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace("'", "\\'");
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
