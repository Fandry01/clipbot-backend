package com.example.clipbot_backend.service.metadata;

import com.example.clipbot_backend.util.MediaPlatform;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MetadataServiceTest {

    @Test
    void resolveCachesResultsForSameUrl() {
        AtomicInteger calls = new AtomicInteger();
        MetadataProvider provider = new MetadataProvider() {
            @Override
            public boolean supports(MediaPlatform platform) {
                return true;
            }

            @Override
            public Optional<MetadataResult> resolve(URI uri, MediaPlatform platform) {
                calls.incrementAndGet();
                return Optional.of(new MetadataResult(platform, uri.toString(), "title", null, null, null));
            }
        };

        MetadataService service = new MetadataService(List.of(provider), Clock.systemUTC());

        MetadataResult first = service.resolve("https://www.youtube.com/watch?v=test");
        MetadataResult second = service.resolve("https://www.youtube.com/watch?v=test");

        assertEquals("title", first.title());
        assertEquals("title", second.title());
        assertEquals(1, calls.get());
    }

    @Test
    void detectPlatformFromShortDomain() {
        MetadataService service = new MetadataService(List.of(), Clock.systemUTC());
        assertEquals(MediaPlatform.YOUTUBE, service.detectPlatform("https://youtu.be/abc"));
        assertEquals(MediaPlatform.OTHER, service.detectPlatform("https://example.com/video"));
    }

    @Test
    void resolveMergesProviderResponses() {
        MetadataProvider titleProvider = new MetadataProvider() {
            @Override
            public boolean supports(MediaPlatform platform) {
                return platform == MediaPlatform.VIMEO;
            }

            @Override
            public Optional<MetadataResult> resolve(URI uri, MediaPlatform platform) {
                return Optional.of(new MetadataResult(platform, uri.toString(), "hello", null, null, null));
            }
        };

        MetadataProvider thumbProvider = new MetadataProvider() {
            @Override
            public boolean supports(MediaPlatform platform) {
                return platform == MediaPlatform.VIMEO;
            }

            @Override
            public Optional<MetadataResult> resolve(URI uri, MediaPlatform platform) {
                return Optional.of(new MetadataResult(platform, uri.toString(), null, null, null, "thumb"));
            }
        };

        MetadataService service = new MetadataService(List.of(titleProvider, thumbProvider), Clock.systemUTC());

        MetadataResult result = service.resolve("https://vimeo.com/12345");

        assertEquals("hello", result.title());
        assertEquals("thumb", result.thumbnail());
        assertEquals(MediaPlatform.VIMEO, result.platform());
    }

    @Test
    void resolveThrowsWhenAllProvidersFail() {
        MetadataProvider provider = new MetadataProvider() {
            @Override
            public boolean supports(MediaPlatform platform) {
                return true;
            }

            @Override
            public Optional<MetadataResult> resolve(URI uri, MediaPlatform platform) {
                throw new MetadataAccessException("boom", new RuntimeException("fail"));
            }
        };

        MetadataService service = new MetadataService(List.of(provider), Clock.systemUTC());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.resolve("https://www.youtube.com/watch?v=boom"));
        assertEquals("METADATA_FETCH_FAILED", ex.getReason());
    }

    @Test
    void resolveRejectsInvalidUrls() {
        MetadataService service = new MetadataService(List.of(), Clock.systemUTC());
        assertThrows(ResponseStatusException.class, () -> service.resolve("not-a-url"));
    }
}
