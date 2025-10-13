package com.example.clipbot_backend.service.metadata;

import com.example.clipbot_backend.util.MediaPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MetadataService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final List<MetadataProvider> providers;
    private final Clock clock;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public MetadataService(List<MetadataProvider> providers) {
        this(providers, Clock.systemUTC());
    }

    MetadataService(List<MetadataProvider> providers, Clock clock) {
        this.providers = new ArrayList<>(providers);
        AnnotationAwareOrderComparator.sort(this.providers);
        this.clock = clock;
    }

    public MetadataResult resolve(String rawUrl) {
        URI uri = parseUrl(rawUrl);
        String normalizedUrl = uri.toString();
        CacheEntry cached = cache.get(normalizedUrl);
        if (cached != null && !cached.isExpired(clock.instant())) {
            return cached.result();
        }

        MediaPlatform platform = detectPlatform(uri);
        MetadataResult result = MetadataResult.empty(platform, normalizedUrl);
        boolean hasProviderResponse = false;
        RuntimeException lastFailure = null;

        for (MetadataProvider provider : providers) {
            if (!provider.supports(platform)) {
                continue;
            }
            try {
                Optional<MetadataResult> partial = provider.resolve(uri, platform);
                if (partial.isPresent()) {
                    result = result.merge(partial.get());
                    hasProviderResponse = true;
                }
            } catch (MetadataAccessException ex) {
                lastFailure = ex;
                LOGGER.warn("Metadata provider {} failed for {}: {}", provider.getClass().getSimpleName(), normalizedUrl, ex.getMessage());
            }
        }

        if (!hasProviderResponse && lastFailure != null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "METADATA_FETCH_FAILED", lastFailure);
        }

        cache.put(normalizedUrl, new CacheEntry(result, clock.instant().plus(CACHE_TTL)));
        return result;
    }

    public MediaPlatform detectPlatform(String rawUrl) {
        URI uri = parseUrl(rawUrl);
        return detectPlatform(uri);
    }

    public String normalizeUrl(String rawUrl) {
        return parseUrl(rawUrl).toString();
    }

    private MediaPlatform detectPlatform(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            return MediaPlatform.OTHER;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.contains("youtube") || normalized.equals("youtu.be")) {
            return MediaPlatform.YOUTUBE;
        }
        if (normalized.contains("vimeo")) {
            return MediaPlatform.VIMEO;
        }
        if (normalized.equals("x.com") || normalized.contains("twitter")) {
            return MediaPlatform.X;
        }
        if (normalized.contains("facebook") || normalized.contains("fb.watch")) {
            return MediaPlatform.FACEBOOK;
        }
        if (normalized.contains("twitch")) {
            return MediaPlatform.TWITCH;
        }
        if(normalized.contains("linkedin")){
            return MediaPlatform.LINKEDIN;
        }
        if (normalized.contains("rumble")) {
            return MediaPlatform.RUMBLE;
        }
        return MediaPlatform.OTHER;
    }

    private URI parseUrl(String rawUrl) {
        if (rawUrl == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL_INVALID");
        }
        String trimmed = rawUrl.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL_INVALID");
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL_INVALID", e);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "METADATA_UNSUPPORTED_HOST");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL_INVALID");
        }
        return uri.normalize();
    }

    private record CacheEntry(MetadataResult result, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }
}
