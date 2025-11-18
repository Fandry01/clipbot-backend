package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.SubtitleFiles;
import com.example.clipbot_backend.dto.WordsParser;
import com.example.clipbot_backend.model.Transcript;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.example.clipbot_backend.service.Interfaces.SubtitleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SubtitleServiceImpl implements SubtitleService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubtitleServiceImpl.class);
    private static final long MIN_CUE_DURATION_MS = 500L;
    private static final long MAX_GAP_MS = 1500L;
    private static final int MAX_LINE_CHARS = 38;

    private final StorageService storageService;

    public SubtitleServiceImpl(StorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public SubtitleFiles buildSubtitles(Transcript transcript, long startMs, long endMs) {
        if (transcript == null || startMs >= endMs) {
            return null;
        }

        List<WordsParser.WordAdapter> words = WordsParser.extract(transcript);
        if (words.isEmpty()) {
            LOGGER.debug("No words available for transcript {} – skipping subtitles", transcript.getId());
            return null;
        }

        List<Cue> cues = buildCues(words, startMs, endMs);
        if (cues.isEmpty()) {
            return null;
        }

        String baseName = "subtitles/" + transcript.getMedia().getId() + "/" + UUID.randomUUID();
        Path srtTmp = null;
        Path vttTmp = null;
        try {
            srtTmp = Files.createTempFile("clipbot-", ".srt");
            vttTmp = Files.createTempFile("clipbot-", ".vtt");

            Files.writeString(srtTmp, buildSrt(cues, startMs), StandardCharsets.UTF_8);
            Files.writeString(vttTmp, buildVtt(cues, startMs), StandardCharsets.UTF_8);

            String srtKey = baseName + ".srt";
            String vttKey = baseName + ".vtt";

            storageService.uploadToOut(srtTmp, srtKey);
            storageService.uploadToOut(vttTmp, vttKey);

            long srtSize = Files.size(srtTmp);
            long vttSize = Files.size(vttTmp);

            return new SubtitleFiles(srtKey, srtSize, vttKey, vttSize);
        } catch (IOException e) {
            throw new RuntimeException("Failed to build subtitles", e);
        } finally {
            deleteIfExists(srtTmp);
            deleteIfExists(vttTmp);
        }
    }

    private static List<Cue> buildCues(List<WordsParser.WordAdapter> words, long clipStart, long clipEnd) {
        List<Cue> cues = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        long cueStart = -1L;
        long cueEnd = -1L;
        long lastWordEnd = -1L;

        for (WordsParser.WordAdapter word : words) {
            long wordStart = Math.max(word.startMs, clipStart);
            long wordEnd = Math.min(word.endMs, clipEnd);
            if (wordEnd <= clipStart || wordStart >= clipEnd) {
                continue;
            }

            if (cueStart < 0) {
                cueStart = wordStart;
            }

            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(word.text);
            cueEnd = Math.max(wordEnd, cueEnd);

            boolean boundary = word.text != null && word.text.matches(".*[.!?]$");
            boolean gap = lastWordEnd > 0 && (wordStart - lastWordEnd) > MAX_GAP_MS;

            if (boundary || gap) {
                cues.add(makeCue(cueStart, cueEnd, text.toString(), clipStart, clipEnd));
                text.setLength(0);
                cueStart = -1L;
                cueEnd = -1L;
            }
            lastWordEnd = wordEnd;
        }

        if (text.length() > 0) {
            long start = cueStart >= 0 ? cueStart : clipStart;
            long end = cueEnd > start ? cueEnd : Math.min(clipEnd, start + MIN_CUE_DURATION_MS);
            cues.add(makeCue(start, end, text.toString(), clipStart, clipEnd));
        }

        return cues;
    }

    private static Cue makeCue(long start, long end, String text, long clipStart, long clipEnd) {
        long adjustedStart = Math.max(clipStart, start);
        long adjustedEnd = Math.min(clipEnd, Math.max(adjustedStart + MIN_CUE_DURATION_MS, end));
        return new Cue(adjustedStart, adjustedEnd, text.trim());
    }

    private static String buildSrt(List<Cue> cues, long clipStart) {
        StringBuilder srt = new StringBuilder();
        for (int i = 0; i < cues.size(); i++) {
            Cue cue = cues.get(i);
            srt.append(i + 1).append('\n');
            srt.append(formatSrtTime(cue.start() - clipStart))
               .append(" --> ")
               .append(formatSrtTime(cue.end() - clipStart))
               .append('\n');
            srt.append(formatCueText(cue.text())).append("\n\n");
        }
        return srt.toString();
    }

    private static String buildVtt(List<Cue> cues, long clipStart) {
        StringBuilder vtt = new StringBuilder("WEBVTT\nKind: captions\n\n");
        for (Cue cue : cues) {
            vtt.append(formatVttTime(cue.start() - clipStart))
               .append(" --> ")
               .append(formatVttTime(cue.end() - clipStart))
               .append('\n');
            vtt.append(formatCueText(cue.text())).append("\n\n");
        }
        return vtt.toString();
    }

    private static String formatCueText(String text) {
        if (text == null) {
            return "";
        }

        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty() || normalized.length() <= MAX_LINE_CHARS) {
            return normalized;
        }

        String[] words = normalized.split(" ");
        if (words.length <= 1) {
            return normalized;
        }

        int bestSplit = 1;
        int bestPenalty = Integer.MAX_VALUE;
        int bestMax = Integer.MAX_VALUE;
        int bestBalance = Integer.MAX_VALUE;

        for (int split = 1; split < words.length; split++) {
            int len1 = joinedLength(words, 0, split);
            int len2 = joinedLength(words, split, words.length);

            int penalty = Math.max(0, len1 - MAX_LINE_CHARS) + Math.max(0, len2 - MAX_LINE_CHARS);
            int maxLen = Math.max(len1, len2);
            int balance = Math.abs(len1 - len2);

            if (penalty < bestPenalty
                    || (penalty == bestPenalty && maxLen < bestMax)
                    || (penalty == bestPenalty && maxLen == bestMax && balance < bestBalance)) {
                bestPenalty = penalty;
                bestMax = maxLen;
                bestBalance = balance;
                bestSplit = split;
            }
        }

        String line1 = join(words, 0, bestSplit);
        String line2 = join(words, bestSplit, words.length);
        return line1 + "\n" + line2;
    }

    private static int joinedLength(String[] words, int start, int end) {
        int len = 0;
        for (int i = start; i < end; i++) {
            len += words[i].length();
            if (i < end - 1) {
                len += 1; // space
            }
        }
        return len;
    }

    private static String join(String[] words, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(words[i]);
        }
        return sb.toString();
    }

    private static String formatSrtTime(long offsetMs) {
        long safeMs = Math.max(0, offsetMs);
        long hours = safeMs / 3_600_000;
        long minutes = (safeMs % 3_600_000) / 60_000;
        long seconds = (safeMs % 60_000) / 1000;
        long millis = safeMs % 1000;
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis);
    }

    private static String formatVttTime(long offsetMs) {
        long safeMs = Math.max(0, offsetMs);
        long hours = safeMs / 3_600_000;
        long minutes = (safeMs % 3_600_000) / 60_000;
        long seconds = (safeMs % 60_000) / 1000;
        long millis = safeMs % 1000;
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis);
    }

    private static void deleteIfExists(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOGGER.warn("Failed to delete temp subtitle file {}", file, e);
        }
    }

    private record Cue(long start, long end, String text) {}
}

