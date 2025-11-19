package com.example.clipbot_backend.service.impl;

import com.example.clipbot_backend.dto.ClipSummary;
import com.example.clipbot_backend.dto.RecommendationResult;
import com.example.clipbot_backend.dto.SubtitleFiles;
import com.example.clipbot_backend.dto.WordsParser;
import com.example.clipbot_backend.model.Clip;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Segment;
import com.example.clipbot_backend.model.Transcript;
import com.example.clipbot_backend.repository.ClipRepository;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.SegmentRepository;
import com.example.clipbot_backend.repository.TranscriptRepository;
import com.example.clipbot_backend.selector.GoodClipSelector;
import com.example.clipbot_backend.selector.ScoredWindow;
import com.example.clipbot_backend.selector.SelectorConfig;
import com.example.clipbot_backend.selector.Window;
import com.example.clipbot_backend.service.JobService;
import com.example.clipbot_backend.service.RecommendationService;
import com.example.clipbot_backend.service.Interfaces.SubtitleService;
import com.example.clipbot_backend.util.ClipStatus;
import com.example.clipbot_backend.util.JobType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link RecommendationService} that builds windows from transcripts and segments.
 */
@Service
public class RecommendationServiceImpl implements RecommendationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecommendationServiceImpl.class);
    private static final int DEFAULT_TOP_N = 6;
    private static final long MIN_WINDOW_MS = 15_000L;
    private static final long MAX_WINDOW_MS = 60_000L;
    private static final double DEFAULT_CONFIDENCE = 0.75;
    private static final double MAX_WORDS_PER_SECOND = 3.5;
    private static final String DEFAULT_RENDER_PROFILE = "youtube-720p";

    private final MediaRepository mediaRepo;
    private final ClipRepository clipRepo;
    private final SegmentRepository segmentRepo;
    private final TranscriptRepository transcriptRepo;
    private final SubtitleService subtitleService;
    private final JobService jobService;
    private final GoodClipSelector goodClipSelector;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;

    public RecommendationServiceImpl(MediaRepository mediaRepo,
                                     ClipRepository clipRepo,
                                     SegmentRepository segmentRepo,
                                     TranscriptRepository transcriptRepo,
                                     SubtitleService subtitleService,
                                     JobService jobService,
                                     GoodClipSelector goodClipSelector,
                                     ObjectMapper objectMapper,
                                     TransactionTemplate txTemplate) {
        this.mediaRepo = mediaRepo;
        this.clipRepo = clipRepo;
        this.segmentRepo = segmentRepo;
        this.transcriptRepo = transcriptRepo;
        this.subtitleService = subtitleService;
        this.jobService = jobService;
        this.goodClipSelector = goodClipSelector;
        this.objectMapper = objectMapper;
        this.txTemplate = txTemplate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RecommendationResult computeRecommendations(UUID mediaId, int topN, @Nullable Map<String, Object> profile, boolean enqueueRender) {
        Objects.requireNonNull(mediaId, "mediaId");
        int limit = topN > 0 ? topN : DEFAULT_TOP_N;
        Map<String, Object> effectiveProfile = profile == null ? Map.of() : profile;
        long started = System.nanoTime();
        RecommendationInput input = loadInput(mediaId);
        SelectorConfig baseCfg = SelectorConfig.defaults();
        Set<String> keywordPool = deriveKeywords(input.words(), effectiveProfile);
        SelectorConfig selectorConfig = new SelectorConfig(
                baseCfg.targetDurationSec(),
                baseCfg.minSpeechDensity(),
                baseCfg.maxSilencePenalty(),
                keywordPool,
                baseCfg.weights()
        );

        List<Window> windows = buildWindows(input.media(), input.segments(), input.words(), selectorConfig);
        List<ScoredWindow> scored = goodClipSelector.selectTop(windows, limit, selectorConfig);
        LOGGER.info("RecommendationService start media={} windows={} selected={} topScores={}",
                mediaId,
                windows.size(),
                scored.size(),
                scored.stream().map(sw -> String.format(Locale.ROOT, "%.3f", sw.score())).collect(Collectors.joining(",")));

        List<ClipSummary> summaries = new ArrayList<>();
        Transcript transcript = input.transcript();
        String profileHash = computeProfileHash(effectiveProfile);
        String renderProfile = resolveRenderProfile(effectiveProfile);

        for (ScoredWindow scoredWindow : scored) {
            Window window = scoredWindow.window();
            UpsertOutcome outcome = upsertClip(mediaId, profileHash, scoredWindow.score(), window, effectiveProfile);
            LOGGER.info("RecommendationService upsert clip start={} end={} profileHash={} created={} score={}",
                    window.startMs(), window.endMs(), profileHash, outcome.created(), outcome.score());
            summaries.add(new ClipSummary(outcome.clipId(), window.startMs(), window.endMs(), outcome.score(), outcome.status().name(), profileHash));

            if (enqueueRender && outcome.created()) {
                SubtitleFiles subs = null;
                if (transcript != null) {
                    subs = subtitleService.buildSubtitles(transcript, window.startMs(), window.endMs());
                    if (subs != null) {
                        LOGGER.info("RecommendationService subtitles built clip={} srtKey={} vttKey={}",
                                outcome.clipId(), subs.srtKey(), subs.vttKey());
                    }
                }
                enqueueRender(outcome.clipId(), mediaId, renderProfile);
            } else if (enqueueRender) {
                LOGGER.info("RecommendationService render skipped clip={} reason=existing", outcome.clipId());
            }
        }

        long durationMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
        LOGGER.info("RecommendationService done media={} clips={} durMs={}", mediaId, summaries.size(), durationMs);
        return new RecommendationResult(mediaId, summaries.size(), summaries);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Page<ClipSummary> listRecommendations(UUID mediaId, Pageable pageable) {
        Objects.requireNonNull(mediaId, "mediaId");
        Pageable effective = pageable == null ? Pageable.unpaged() : pageable;
        Page<Clip> page = clipRepo.findPageByMediaId(mediaId, effective);
        List<ClipSummary> summaries = page.getContent().stream()
                .map(clip -> new ClipSummary(clip.getId(), clip.getStartMs(), clip.getEndMs(), clip.getScore(), clip.getStatus().name(), clip.getProfileHash()))
                .toList();
        return new PageImpl<>(summaries, effective, page.getTotalElements());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> explain(UUID mediaId, long startMs, long endMs) {
        Objects.requireNonNull(mediaId, "mediaId");
        RecommendationInput input = loadInput(mediaId);
        SelectorConfig baseCfg = SelectorConfig.defaults();
        Set<String> keywordPool = deriveKeywords(input.words(), Map.of());
        SelectorConfig selectorConfig = new SelectorConfig(
                baseCfg.targetDurationSec(),
                baseCfg.minSpeechDensity(),
                baseCfg.maxSilencePenalty(),
                keywordPool,
                baseCfg.weights()
        );
        List<Window> windows = buildWindows(input.media(), input.segments(), input.words(), selectorConfig);
        if (windows.isEmpty()) {
            return Map.of();
        }
        goodClipSelector.selectTop(windows, windows.size(), selectorConfig);
        String key = startMs + "-" + endMs;
        return goodClipSelector.explainLast().entrySet().stream()
                .filter(entry -> entry.getKey().equals(key))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void enqueueRender(UUID clipId, UUID mediaId, String profile) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("clipId", clipId.toString());
        payload.put("profile", profile);
        jobService.enqueueUnique(mediaId, JobType.CLIP, "clip:" + clipId, payload);
        LOGGER.info("RecommendationService enqueue render clip={} profile={}", clipId, profile);
    }

    private UpsertOutcome upsertClip(UUID mediaId, String profileHash, double score, Window window, Map<String, Object> profile) {
        return txTemplate.execute(status -> {
            Clip existing = clipRepo.findByMediaIdAndStartMsAndEndMsAndProfileHash(mediaId, window.startMs(), window.endMs(), profileHash)
                    .orElse(null);
            BigDecimal newScore = BigDecimal.valueOf(score).setScale(3, RoundingMode.HALF_UP);
            if (existing != null) {
                BigDecimal oldScore = existing.getScore();
                if (oldScore == null || oldScore.compareTo(newScore) < 0) {
                    existing.setScore(newScore);
                    clipRepo.save(existing);
                }
                return new UpsertOutcome(existing.getId(), existing.getStatus(), newScore, false);
            }

            Media mediaRef = mediaRepo.getReferenceById(mediaId);
            Clip clip = new Clip(mediaRef, window.startMs(), window.endMs());
            clip.setStatus(ClipStatus.QUEUED);
            clip.setProfileHash(profileHash);
            clip.setScore(newScore);
            clip.setMeta(buildMeta(profile));
            Clip saved = clipRepo.save(clip);
            return new UpsertOutcome(saved.getId(), saved.getStatus(), newScore, true);
        });
    }

    private static Map<String, Object> buildMeta(Map<String, Object> profile) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("source", "auto-reco");
        if (profile != null && !profile.isEmpty()) {
            meta.put("profile", profile);
        }
        return meta;
    }

    private static Set<String> deriveKeywords(List<WordsParser.WordAdapter> words, Map<String, Object> profile) {
        if ((words == null || words.isEmpty()) && (profile == null || profile.get("keywords") == null)) {
            return Set.of();
        }
        Map<String, Integer> counts = new HashMap<>();
        if (words != null) {
            for (WordsParser.WordAdapter word : words) {
                String cleaned = normalizeWord(word.text);
                if (cleaned.isEmpty()) {
                    continue;
                }
                counts.merge(cleaned, 1, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> sorted = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(8)
                .toList();
        Set<String> result = new HashSet<>();
        sorted.forEach(entry -> result.add(entry.getKey()));
        extractProfileKeywords(profile, result);
        return Set.copyOf(result);
    }

    private static void extractProfileKeywords(Map<String, Object> profile, Set<String> target) {
        if (profile == null || profile.isEmpty()) {
            return;
        }
        Object keywords = profile.get("keywords");
        if (keywords instanceof Collection<?> collection) {
            collection.stream()
                    .map(Object::toString)
                    .map(RecommendationServiceImpl::normalizeWord)
                    .filter(s -> !s.isEmpty())
                    .forEach(target::add);
        } else if (keywords instanceof String text) {
            for (String token : text.split(",")) {
                String cleaned = normalizeWord(token);
                if (!cleaned.isEmpty()) {
                    target.add(cleaned);
                }
            }
        }
    }

    private List<Window> buildWindows(MediaSnapshot media, List<SegmentSnapshot> segments, List<WordsParser.WordAdapter> words, SelectorConfig config) {
        long targetMs = config.targetDurationSec() * 1000L;
        long minMs = Math.max(MIN_WINDOW_MS, targetMs - 5_000L);
        long maxMs = Math.min(MAX_WINDOW_MS, targetMs + 5_000L);
        long duration = media.durationMs() != null && media.durationMs() > 0 ? media.durationMs() : estimateDuration(segments, words, maxMs);
        List<Window> windows = new ArrayList<>();
        Set<String> keywordPool = config.boostKeywords() == null ? Set.of() : config.boostKeywords();

        if (segments != null && !segments.isEmpty()) {
            for (SegmentSnapshot snapshot : segments) {
                windows.addAll(fromSegment(snapshot, minMs, maxMs, duration, words, keywordPool));
            }
        }

        if (windows.isEmpty()) {
            windows.addAll(slidingWindows(duration, minMs, maxMs, words, keywordPool));
        }

        Map<String, Window> deduped = new LinkedHashMap<>();
        for (Window window : windows) {
            String key = window.startMs() + ":" + window.endMs();
            deduped.putIfAbsent(key, window);
        }
        return new ArrayList<>(deduped.values());
    }

    private List<Window> fromSegment(SegmentSnapshot segment, long minMs, long maxMs, long duration, List<WordsParser.WordAdapter> words, Set<String> keywordPool) {
        List<Window> result = new ArrayList<>();
        long span = Math.max(segment.endMs() - segment.startMs(), minMs);
        long windowLength = clamp(span, minMs, maxMs);
        long start = Math.max(0, segment.startMs() - (windowLength - span) / 2);
        long end = Math.min(duration, start + windowLength);
        result.add(buildWindow(start, end, words, keywordPool));
        if (span > maxMs) {
            long step = Math.max(minMs, maxMs - 5_000L);
            for (long offset = segment.startMs(); offset < segment.endMs(); offset += step) {
                long nextStart = Math.min(offset, Math.max(0, duration - minMs));
                long nextEnd = Math.min(duration, nextStart + windowLength);
                if (nextEnd - nextStart >= minMs) {
                    result.add(buildWindow(nextStart, nextEnd, words, keywordPool));
                }
            }
        }
        return result;
    }

    private List<Window> slidingWindows(long duration, long minMs, long maxMs, List<WordsParser.WordAdapter> words, Set<String> keywordPool) {
        if (duration <= 0) {
            return List.of();
        }
        List<Window> windows = new ArrayList<>();
        long step = Math.max(minMs, (minMs + maxMs) / 2);
        for (long start = 0; start < duration; start += step) {
            long end = Math.min(duration, start + maxMs);
            if (end - start >= minMs) {
                windows.add(buildWindow(start, end, words, keywordPool));
            }
        }
        return windows;
    }

    private Window buildWindow(long start, long end, List<WordsParser.WordAdapter> words, Set<String> keywordPool) {
        long duration = Math.max(1, end - start);
        double speechMs = 0.0;
        double sumConfidence = 0.0;
        int wordCount = 0;
        int excitedCount = 0;
        int uppercaseCount = 0;
        Set<String> matchedKeywords = new HashSet<>();

        if (words != null) {
            for (WordsParser.WordAdapter word : words) {
                long overlapStart = Math.max(start, word.startMs);
                long overlapEnd = Math.min(end, word.endMs);
                if (overlapEnd <= overlapStart) {
                    continue;
                }
                speechMs += overlapEnd - overlapStart;
                double confidence = word.confidence == null ? DEFAULT_CONFIDENCE : word.confidence;
                sumConfidence += confidence;
                wordCount++;
                String text = word.text == null ? "" : word.text;
                if (text.matches(".*[!?].*")) {
                    excitedCount++;
                }
                if (text.chars().anyMatch(Character::isUpperCase)) {
                    uppercaseCount++;
                }
                String normalized = normalizeWord(text);
                if (!normalized.isEmpty() && keywordPool.contains(normalized)) {
                    matchedKeywords.add(normalized);
                }
            }
        }

        double speechDensity = clampDouble(speechMs / duration);
        double avgConfidence = wordCount == 0 ? 0.0 : clampDouble(sumConfidence / wordCount);
        double wordsPerSecond = wordCount / Math.max(1.0, duration / 1000.0);
        double energy = clampDouble((wordsPerSecond / MAX_WORDS_PER_SECOND) + Math.min(0.2, excitedCount * 0.05 + uppercaseCount * 0.02));
        double silencePenalty = clampDouble(1.0 - speechDensity);
        return new Window(start, end, speechDensity, avgConfidence, energy, silencePenalty, Set.copyOf(matchedKeywords));
    }

    private static long estimateDuration(List<SegmentSnapshot> segments, List<WordsParser.WordAdapter> words, long fallback) {
        long max = fallback;
        if (segments != null) {
            for (SegmentSnapshot snapshot : segments) {
                max = Math.max(max, snapshot.endMs());
            }
        }
        if (words != null) {
            for (WordsParser.WordAdapter word : words) {
                max = Math.max(max, word.endMs);
            }
        }
        return max;
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private static String normalizeWord(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        return normalized.length() < 4 ? "" : normalized;
    }

    private String computeProfileHash(Map<String, Object> profile) {
        if (profile == null || profile.isEmpty()) {
            return "";
        }
        try {
            Object canonical = canonicalize(profile);
            String json = objectMapper.writeValueAsString(canonical);
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format(Locale.ROOT, "%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        } catch (Exception e) {
            throw new IllegalStateException("PROFILE_HASH_FAILED", e);
        }
    }

    private static Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                sorted.put(entry.getKey().toString(), canonicalize(entry.getValue()));
            }
            return sorted;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> normalized = new ArrayList<>(collection.size());
            for (Object item : collection) {
                normalized.add(canonicalize(item));
            }
            return normalized;
        }
        return value;
    }

    private static String resolveRenderProfile(Map<String, Object> profile) {
        if (profile != null) {
            Object profileValue = profile.get("profile");
            if (profileValue instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return DEFAULT_RENDER_PROFILE;
    }

    @Transactional(readOnly = true)
    protected RecommendationInput loadInput(UUID mediaId) {
        Media media = mediaRepo.findById(mediaId).orElseThrow(() -> new IllegalArgumentException("MEDIA_NOT_FOUND"));
        List<Segment> rawSegments = segmentRepo.findByMedia(media, Pageable.unpaged()).getContent();
        List<SegmentSnapshot> segments = rawSegments.stream()
                .map(segment -> new SegmentSnapshot(segment.getStartMs(), segment.getEndMs()))
                .toList();
        Transcript transcript = transcriptRepo.findTopByMediaIdOrderByCreatedAtDesc(mediaId).orElse(null);
        if (transcript != null && transcript.getMedia() != null) {
            transcript.getMedia().getId();
        }
        List<WordsParser.WordAdapter> words = WordsParser.extract(transcript);
        MediaSnapshot mediaSnapshot = new MediaSnapshot(media.getId(), media.getDurationMs());
        return new RecommendationInput(mediaSnapshot, transcript, segments, words);
    }

    private record RecommendationInput(MediaSnapshot media,
                                       Transcript transcript,
                                       List<SegmentSnapshot> segments,
                                       List<WordsParser.WordAdapter> words) {
    }

    private record MediaSnapshot(UUID id, Long durationMs) {
    }

    private record SegmentSnapshot(long startMs, long endMs) {
    }

    private record UpsertOutcome(UUID clipId, ClipStatus status, BigDecimal score, boolean created) {
    }
}
