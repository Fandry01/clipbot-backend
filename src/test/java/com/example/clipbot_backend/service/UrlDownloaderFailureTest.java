package com.example.clipbot_backend.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.example.clipbot_backend.service.Interfaces.StorageService;
import java.io.IOException;
import java.nio.file.Files;
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
    void youtubeDownloadFailureSurfacesAuthWall() {
        Path target = tempDir.resolve("ext/yt/video/source.mp4");
        when(storageService.resolveRaw("ext/yt/video/source.mp4")).thenReturn(target);

        UrlDownloader downloader = new TestDownloader(storageService,
                "Sign in to confirm you're not a bot.", 1, false, target, null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                downloader.ensureRawObject("https://www.youtube.com/watch?v=video", "ext/yt/video/source.mp4"));

        String message = ex.getMessage();
        assertTrue(message.contains("authentication/cookies"));
        assertTrue(message.contains("Sign in to confirm you're not a bot."));
    }

    @Test
    void youtubeDownloadFailureSurfacesAuthWallWithSmartApostrophe() {
        Path target = tempDir.resolve("ext/yt/video/source.mp4");
        when(storageService.resolveRaw("ext/yt/video/source.mp4")).thenReturn(target);

        UrlDownloader downloader = new TestDownloader(storageService,
                "Sign in to confirm you’re not a bot.", 1, false, target, null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                downloader.ensureRawObject("https://www.youtube.com/watch?v=video", "ext/yt/video/source.mp4"));

        String message = ex.getMessage();
        assertTrue(message.contains("authentication/cookies"));
        assertTrue(message.contains("Sign in to confirm you’re not a bot."));
    }

    @Test
    void youtubeDownloadTimeoutIsSurfaced() {
        Path target = tempDir.resolve("ext/yt/video/source.mp4");
        when(storageService.resolveRaw("ext/yt/video/source.mp4")).thenReturn(target);

        UrlDownloader downloader = new TestDownloader(storageService,
                "processing...", -1, true, target, null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                downloader.ensureRawObject("https://www.youtube.com/watch?v=video", "ext/yt/video/source.mp4"));

        assertTrue(ex.getMessage().toLowerCase().contains("timeout"));
    }

    @Test
    void cookiesFileIsAddedWhenPresent() {
        Path target = tempDir.resolve("ext/yt/video/source.mp4");
        when(storageService.resolveRaw("ext/yt/video/source.mp4")).thenReturn(target);

        Path cookies = tempDir.resolve("cookies.txt");
        assertDoesNotThrow(() -> Files.writeString(cookies, "dummy"));

        CapturingDownloader downloader = new CapturingDownloader(storageService,
                "ok", 0, false, target, cookies);

        assertDoesNotThrow(() -> downloader.ensureRawObject("https://www.youtube.com/watch?v=video", "ext/yt/video/source.mp4"));

        assertTrue(downloader.lastCmd.contains("--cookies"));
        int idx = downloader.lastCmd.indexOf("--cookies");
        assertEquals(cookies.toAbsolutePath().toString(), downloader.lastCmd.get(idx + 1));
        assertEquals("https://www.youtube.com/watch?v=video", downloader.lastCmd.get(downloader.lastCmd.size() - 1));
        assertTrue(idx < downloader.lastCmd.size() - 1);
        int urlIdx = downloader.lastCmd.indexOf("https://www.youtube.com/watch?v=video");
        int outputIdx = downloader.lastCmd.indexOf("-o");
        assertTrue(idx < outputIdx);
        assertTrue(urlIdx > outputIdx);
    }

    @Test
    void youtubeDownloadTimeoutKillsProcess() {
        Path target = tempDir.resolve("ext/yt/video/source.mp4");
        when(storageService.resolveRaw("ext/yt/video/source.mp4")).thenReturn(target);

        UrlDownloader downloader = new RealProcessDownloader(storageService, tempDir);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                downloader.ensureRawObject("https://www.youtube.com/watch?v=video", "ext/yt/video/source.mp4"));

        assertTrue(ex.getMessage().toLowerCase().contains("timeout"));
    }

    @Test
    void partialFilesAreMentionedAndDeletedOnFailure() throws IOException {
        Path target = tempDir.resolve("ext/yt/video/source.mp4");
        when(storageService.resolveRaw("ext/yt/video/source.mp4")).thenReturn(target);
        Path partial = target.resolveSibling(target.getFileName().toString() + ".part");
        Files.createDirectories(partial.getParent());
        Files.createFile(partial);

        UrlDownloader downloader = new TestDownloader(storageService,
                "error", 1, false, target, null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                downloader.ensureRawObject("https://www.youtube.com/watch?v=video", "ext/yt/video/source.mp4"));

        assertTrue(ex.getMessage().contains(partial.toString()));
        assertTrue(Files.notExists(partial));
    }

    private static class TestDownloader extends UrlDownloader {
        private final ProcessResult stubResult;

        TestDownloader(StorageService storageService, String log, int code, boolean timeout, Path target, Path cookies) {
            super(storageService, "yt-dlp", 120, "JUnit-UA", 3, "ffmpeg", cookies != null ? cookies.toString() : null);
            this.stubResult = new ProcessResult(code, log, timeout);
            this.target = target;
        }

        private final Path target;

        @Override
        protected ProcessResult runProcess(List<String> cmd, long timeoutMinutes) {
            try {
                Files.createDirectories(target.getParent());
                if (stubResult.code() == 0 && !stubResult.timedOut()) {
                    Files.createFile(target);
                }
            } catch (IOException ignored) {
            }
            return stubResult;
        }
    }

    private static final class CapturingDownloader extends TestDownloader {
        private List<String> lastCmd;

        CapturingDownloader(StorageService storageService, String log, int code, boolean timeout, Path target, Path cookies) {
            super(storageService, log, code, timeout, target, cookies);
        }

        @Override
        protected ProcessResult runProcess(List<String> cmd, long timeoutMinutes) {
            this.lastCmd = cmd;
            return super.runProcess(cmd, timeoutMinutes);
        }
    }

    private static final class RealProcessDownloader extends UrlDownloader {

        RealProcessDownloader(StorageService storageService, Path tempDir) {
            super(storageService, "yt-dlp", 120, "JUnit-UA", 3, "ffmpeg", null);
            this.tempDir = tempDir;
        }

        private final Path tempDir;

        @Override
        protected ProcessResult runProcess(List<String> cmd, long timeoutMinutes) throws IOException, InterruptedException {
            List<String> sleepCmd = List.of("sh", "-c", "sleep 2");
            return super.runProcess(sleepCmd, 0);
        }

        @Override
        public Path ensureRawObject(String url, String objectKey) {
            Path target = tempDir.resolve(objectKey);
            try {
                Files.createDirectories(target.getParent());
            } catch (IOException ignored) {
            }
            return super.ensureRawObject(url, objectKey);
        }
    }
}
