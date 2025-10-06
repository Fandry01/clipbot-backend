package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.dto.web.TranscriptionResult;
import com.example.clipbot_backend.engine.Interfaces.TranscriptionEngine;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class WhisperLocalTranscriptionEngine implements TranscriptionEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(WhisperLocalTranscriptionEngine.class);

    private final StorageService storageService;
    private final String ffmpegBin;
    private final String whisperCmd;
    private final String whisperModel;
    private final Duration timeout;
    private final Path workDir;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WhisperLocalTranscriptionEngine(StorageService storageService, String ffmpegBin, String whisperCmd, String whisperModel, Duration timeout, Path workDir) {
        this.storageService = Objects.requireNonNull(storageService);
        this.ffmpegBin = ffmpegBin != null ? ffmpegBin : "ffmpeg";
        this.whisperCmd = whisperCmd != null ? whisperCmd : "whisper";
        this.whisperModel = whisperModel != null ? whisperModel : "medium";
        this.timeout = timeout  != null ? timeout : Duration.ofMinutes(5);
        this.workDir = (workDir != null) ? workDir : Path.of("./data/work").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.workDir);} catch (Exception e){
            throw new IllegalStateException("Unable to create work directory: " + this.workDir, e);
        }
    }

    @Override
    public TranscriptionResult transcribe(Path mediaFile, String langHint) throws Exception {
        return null;
    }

    @Override
    public Result transcribe(Request request) throws Exception {
        //1) Bronbestand lokaliseren
        Path input = storageService.resolveRaw(request.objectKey());
        if(!Files.exists(input)) {
            throw new IllegalArgumentException("Input file not found: " + input);
        }

        //2) convert naar wav/16/mono (sneller/robuster)
        Path wav = workDir.resolve("asr-" +request.mediaId() + ".wav");
        runAndLog(List.of(
                ffmpegBin, "-y",
                "-i", input.toAbsolutePath().toString(),
                "-ac", "1", "-ar", "16000",
                wav.toAbsolutePath().toString()

        ), timeout);

        if(!Files.exists(wav)) throw new IllegalArgumentException("ffmpeg failed to create wav");

        // 3) Run whisper CLI -> Json output
        Path outDir = workDir.resolve("asr-out");
        Files.createDirectories(outDir);

        // voorbeeld command voor faster-whisper cli (pip package "whisperx" of "openai-whisper" tooling kan je analoog doen):
        List<String> whisper = new ArrayList<>();
        whisper.add(whisperCmd);
        whisper.add(wav.toAbsolutePath().toString());
        whisper.add("--model"); whisper.add(whisperModel);
        whisper.add("--language"); whisper.add(request.langHint() != null ? request.langHint() : "auto");
        whisper.add("--output_dir"); whisper.add(outDir.toAbsolutePath().toString());
        whisper.add("-word_timestamps"); whisper.add("True");

        runAndLog(whisper, timeout);

        String base = wav.getFileName().toString().replaceAll("\\.wav$", "");
        Path json = outDir.resolve(base + ".json");
        if (!Files.exists(json)) throw new IllegalArgumentException("whisper did not produce JSON at: " + json);

        //4) Parse JSON -> text + words
        JsonNode root = objectMapper.readTree(Files.readString(json));
        Parsed p = parseWhisperJson(root);

        //5) Cleanup Temp (wav/json kun je bewaren voor debug als je wilt)
        try {Files.deleteIfExists(wav);} catch (Exception ignore){}
        //try {Files.deleteIfExists(json);} catch (Exception ignore){}

        Map<String, Object> meta = new HashMap<>();
        meta.put("model", whisperModel);
        meta.put("tool", "whisper-local");

        return new Result(p.text, p.words, p.lang, "whisper-local", meta);

    }

    private void runAndLog(List<String> cmd, Duration timeout) throws InterruptedException, IOException {
        LOGGER.info("Exc: {}", String.join(" ", cmd));
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        Thread thread = new Thread(() -> {
            try (var buffReader = new BufferedReader(new InputStreamReader(process.getInputStream()))){
                    buffReader.lines().forEach(line -> LOGGER.debug("[exec] {}", line));
                }catch (Exception ignore){}

        });
        thread.setDaemon(true);
        thread.start();
        boolean ok = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if(!ok){
            process.destroyForcibly(); throw new RuntimeException("Process timed out: " + cmd);
        }
        if(process.exitValue() != 0){
            throw new RuntimeException("process failed: exit= " + process.exitValue());
        }
    }
    private record Parsed(String text, List<Word> words, String lang) {}

    /**
     * Ondersteunt "typische" whisper JSON varianten:
     * - segments[].text + segments[].words[].{word,start,end}
     * - of direct words[] op root
     * - language veld op root of meta
     */

    private Parsed parseWhisperJson(JsonNode root) {
        StringBuilder all = new StringBuilder();
        List<Word> words = new ArrayList<>();
        String lang = asTextOrNull(root.path("language"));

        // vorm 1: segments[].words[]
        if (root.has("segments") && root.path("segments").isArray()) {
            for (JsonNode seg : root.path("segments")) {
                String segText = asTextOrNull(seg.path("text"));
                if (segText != null) {
                    if (all.length() > 0) all.append(" ");
                    all.append(segText.trim());
                }
                if (seg.has("words")) {
                    for (JsonNode w : seg.path("words")) {
                        String t = firstNonNull(asTextOrNull(w.path("word")), asTextOrNull(w.path("text")));
                        long sMs = (long) Math.round(w.path("start").asDouble() * 1000.0);
                        long eMs = (long) Math.round(w.path("end").asDouble() * 1000.0);
                        if (t != null) words.add(new Word(sMs, eMs, t));
                    }
                }
            }
        }

        // vorm 2: root.words[]
        if (words.isEmpty() && root.has("words")) {
            for (JsonNode w : root.path("words")) {
                String t = firstNonNull(asTextOrNull(w.path("word")), asTextOrNull(w.path("text")));
                long sMs = (long) Math.round(w.path("start").asDouble() * 1000.0);
                long eMs = (long) Math.round(w.path("end").asDouble() * 1000.0);
                if (t != null) words.add(new Word(sMs, eMs, t));
            }
        }

        // tekst fallback (soms root.text)
        if (all.length() == 0) {
            String txt = asTextOrNull(root.path("text"));
            if (txt != null) all.append(txt.trim());
        }

        return new Parsed(all.toString(), words, lang != null ? lang : "auto");
    }

    private static String asTextOrNull(JsonNode n) {
        return (n != null && !n.isMissingNode() && !n.isNull()) ? n.asText() : null;
    }
    private static String firstNonNull(String a, String b) { return a != null ? a : b; }
}
