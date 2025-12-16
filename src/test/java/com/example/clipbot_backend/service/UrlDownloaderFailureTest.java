package com.example.clipbot_backend.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.example.clipbot_backend.service.Interfaces.StorageService;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UrlDownloaderFailureTest {

    @Mock
    private StorageService storageService;

    @TempDir
    private Path tempDir;

    @Test
    void youtubeDownloadFailureSurfacesYtDlpLog() {
        Path target = tempDir.resolve("ext/yt/video/source.mp4");
        when(storageService.resolveRaw("ext/yt/video/source.mp4")).thenReturn(target);

        UrlDownloader downloader = new TestDownloader(storageService,
                "Sign in to confirm you're not a bot.", 1);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                downloader.ensureRawObject("https://www.youtube.com/watch?v=video", "ext/yt/video/source.mp4"));

        String message = ex.getMessage();
        assertTrue(message.contains("yt-dlp exit=1"));
        assertTrue(message.contains("Sign in to confirm you're not a bot."));
    }

    private static final class TestDownloader extends UrlDownloader {
        private final ProcessResult stubResult;

        TestDownloader(StorageService storageService, String log, int code) {
            super(storageService, "yt-dlp", 120, "JUnit-UA", 3, "ffmpeg");
            this.stubResult = new ProcessResult(code, log);
        }

        @Override
        protected ProcessResult runProcess(List<String> cmd) {
            return stubResult;
        }
    }
}
