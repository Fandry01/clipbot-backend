package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.service.FileService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/v1/files")
public class FileController {

    private final FileService files;

    public FileController(FileService files) {
        this.files = files;
    }

    @GetMapping(value = "/out/**", produces = MediaType.ALL_VALUE)
    public ResponseEntity<Resource> getOut(
            HttpServletRequest req,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
            @RequestParam(value = "download", required = false) Integer download
    ) throws IOException {
        String pattern = (String) req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = (String) req.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String objectKey = new AntPathMatcher().extractPathWithinPattern(pattern, path);
        if (objectKey == null || objectKey.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        boolean asDownload = (download != null && download == 1);
        return files.streamOut(objectKey, range, ifNoneMatch, asDownload);
    }

    // (optioneel) HEAD-variant voor betere caching/players
//    @RequestMapping(value = "/out/**", method = RequestMethod.HEAD, produces = MediaType.ALL_VALUE)
//    public ResponseEntity<Resource> headOut(HttpServletRequest req,
//                                            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) throws IOException {
//        String objectKey = extractTailFromWildcard(req, "/v1/files/out/");
//        if (objectKey == null || objectKey.isBlank()) {
//            return ResponseEntity.badRequest().build();
//        }
//        // FileService kan een lichte HEAD-mode ondersteunen (alleen headers).
//        return files.streamOut(objectKey, range, false); // body is Resource; Spring stuurt geen body bij HEAD
//    }

    @GetMapping(value = "/download/out/**", produces = MediaType.ALL_VALUE)
    public ResponseEntity<FileSystemResource> downloadOut(HttpServletRequest req) throws IOException {
        String objectKey = extractTailFromWildcard(req, "/v1/files/download/out/");
        return files.downloadOut(objectKey);
    }

    // ===== util =====
    private static String extractTailFromWildcard(HttpServletRequest req, String prefix) {
        // decode exact één keer; bescherm tegen dubbele leading slashes
        String uri = URLDecoder.decode(req.getRequestURI(), StandardCharsets.UTF_8);
        int i = uri.indexOf(prefix);
        if (i < 0) throw new IllegalArgumentException("Bad path");
        String tail = uri.substring(i + prefix.length());
        while (tail.startsWith("/")) tail = tail.substring(1);
        return tail;
    }
}
