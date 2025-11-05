package com.example.clipbot_backend.service;

import com.example.clipbot_backend.service.Interfaces.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;


@Service
public class FileService {
    private final StorageService storage;

    public FileService(StorageService storage) {
        this.storage = storage;
    }
    /** Streamt bestanden uit "out" (clips/subs/thumbs) met byte-range (seek). */
    public ResponseEntity<Resource> streamOut(String objectKey,
                                              String rangeHeader,
                                              @Nullable String ifNoneMatch,
                                              boolean download) throws IOException {
        if (objectKey == null || objectKey.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        Path file = storage.resolveOut(objectKey);
        if (!Files.exists(file)) throw notFound("out", objectKey);
        assertInside(storage.rootOut(), file);

        FileSystemResource resource = new FileSystemResource(file);
        long length  = resource.contentLength();
        long lastMod = Files.getLastModifiedTime(file).toMillis();

        // Simpele sterke ETag: "lastMod-length"
        String etag = "\"" + lastMod + "-" + length + "\"";

        MediaType contentType = guessType(file);

        String fn = file.getFileName().toString();
        String cdType = download ? "attachment" : "inline";
        String cd = cdType + "; filename=\"" + fn + "\"; filename*=UTF-8''" + URLEncoder.encode(fn, StandardCharsets.UTF_8);

        CacheControl cacheCtl = chooseCache(fn);

        // 304-handling alleen voor non-range
        if ((rangeHeader == null || rangeHeader.isBlank()) && ifNoneMatch != null && !ifNoneMatch.isBlank()) {
            // Kan meerdere etags bevatten, dus 'contains' is prima hier
            if (ifNoneMatch.contains(etag)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .eTag(etag)
                        .lastModified(lastMod)
                        .cacheControl(cacheCtl)
                        .build();
            }
        }

        if (rangeHeader == null || rangeHeader.isBlank()) {
            // 200 OK (volledig)
            return ResponseEntity.ok()
                    .cacheControl(cacheCtl).eTag(etag).lastModified(lastMod)
                    .contentType(contentType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_DISPOSITION, cd)
                    .contentLength(length)
                    .body(resource);
        }

        // 206 Partial Content
        HttpRange r = HttpRange.parseRanges(rangeHeader).get(0);
        long start = r.getRangeStart(length);
        long end   = r.getRangeEnd(length);
        if (start >= length) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + length).build();
        }
        if (end < start) end = length - 1;
        long rangeLen = end - start + 1;

        InputStream is = Files.newInputStream(file);
        long toSkip = start, skipped;
        while (toSkip > 0 && (skipped = is.skip(toSkip)) > 0) toSkip -= skipped;

        InputStreamResource slice = new InputStreamResource(is) {
            @Override public long contentLength() { return rangeLen; }
            @Override public String getFilename() { return fn; }
        };

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .cacheControl(cacheCtl).eTag(etag).lastModified(lastMod)
                .contentType(contentType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + length)
                .header(HttpHeaders.CONTENT_DISPOSITION, cd)
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
        if (name.endsWith(".mp4"))  return MediaType.parseMediaType("video/mp4");
        if (name.endsWith(".m3u8")) return MediaType.parseMediaType("application/vnd.apple.mpegurl");
        if (name.endsWith(".ts"))   return MediaType.parseMediaType("video/mp2t");
        if (name.endsWith(".vtt"))  return MediaType.parseMediaType("text/vtt; charset=utf-8");
        if (name.endsWith(".srt"))  return MediaType.TEXT_PLAIN;
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (name.endsWith(".png"))  return MediaType.IMAGE_PNG;
        String probe = Files.probeContentType(file);
        return MediaType.parseMediaType(probe != null ? probe : "application/octet-stream");
    }


    private static String inline(Path file) {
        return "inline; filename=\"" + file.getFileName() + "\"";
    }

    private static String attachment(Path file) {
        return "attachment; filename=\"" + file.getFileName() + "\"";
    }
    private CacheControl chooseCache(String filename) {
        String ext = "";
        int dot = filename.lastIndexOf('.');
        if (dot >= 0 && dot < filename.length() - 1) {
            ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
        switch (ext) {
            case "mp4":
            case "webm":
            case "m4a":
            case "mp3":
            case "wav":
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "webp":
            case "vtt":
            case "srt":
                // 1 jaar, public, immutable
                return CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable();
            default:
                // dynamischer spul
                return CacheControl.noCache();
        }
    }
}
