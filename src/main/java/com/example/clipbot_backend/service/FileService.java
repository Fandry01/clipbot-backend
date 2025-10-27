package com.example.clipbot_backend.service;

import com.example.clipbot_backend.service.Interfaces.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


@Service
public class FileService {
    private final StorageService storage;

    public FileService(StorageService storage) {
        this.storage = storage;
    }
    /** Streamt bestanden uit "out" (clips/subs/thumbs) met byte-range (seek). */
    public ResponseEntity<Resource> streamOut(String objectKey, String rangeHeader) throws IOException {
        Path file = storage.resolveOut(objectKey);
        if (!Files.exists(file)) throw notFound("out", objectKey);
        assertInside(storage.resolveOut(""), file);

        MediaType contentType = guessType(file);
        FileSystemResource resource = new FileSystemResource(file);
        long length = resource.contentLength();

        if (rangeHeader == null || rangeHeader.isBlank()) {
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_DISPOSITION, inline(file))
                    .contentLength(length)
                    .body(resource);
        }

        // 206 Partial Content
        List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
        HttpRange r = ranges.get(0);
        long start = r.getRangeStart(length);
        long end   = r.getRangeEnd(length);
        if (end < start) end = length - 1;
        long rangeLen = end - start + 1;

        InputStream is = Files.newInputStream(file);
        if (start > 0) is.skip(start);
        InputStreamResource slice = new InputStreamResource(is) {
            @Override public long contentLength() { return rangeLen; }
            @Override public String getFilename() { return file.getFileName().toString(); }
        };

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(contentType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + length)
                .header(HttpHeaders.CONTENT_DISPOSITION, inline(file))
                .contentLength(rangeLen)
                .body(slice);
    }
    public ResponseEntity<FileSystemResource> downloadOut(String objectKey) throws IOException {
        Path file = storage.resolveOut(objectKey);
        if (!Files.exists(file)) throw notFound("out", objectKey);
        assertInside(storage.resolveOut(""), file);

        MediaType contentType = guessType(file);
        FileSystemResource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(file))
                .body(resource);
    }
    private static ResponseStatusException notFound(String bucket, String key) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: " + bucket + "/" + key);
    }

    private static void assertInside(Path baseDir, Path target) {
        Path base = baseDir.toAbsolutePath().normalize();
        Path norm = target.toAbsolutePath().normalize();
        if (!norm.startsWith(base)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden path");
        }
    }

    private static MediaType guessType(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".mp4")) return MediaType.valueOf("video/mp4");
        if (name.endsWith(".m3u8")) return MediaType.valueOf("application/x-mpegURL");
        if (name.endsWith(".vtt")) return MediaType.valueOf("text/vtt; charset=utf-8");
        if (name.endsWith(".srt")) return MediaType.TEXT_PLAIN;
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (name.endsWith(".png")) return MediaType.IMAGE_PNG;
        String probe = Files.probeContentType(file);
        return MediaType.parseMediaType(probe != null ? probe : "application/octet-stream");
    }

    private static String inline(Path file) {
        return "inline; filename=\"" + file.getFileName() + "\"";
    }

    private static String attachment(Path file) {
        return "attachment; filename=\"" + file.getFileName() + "\"";
    }
}
