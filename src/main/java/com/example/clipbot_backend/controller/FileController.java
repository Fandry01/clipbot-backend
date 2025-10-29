package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.service.FileService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
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

    @GetMapping("/out/**")
    public ResponseEntity<Resource> getOut(
            HttpServletRequest req,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            @RequestParam(value = "download", required = false) Integer download
    ) throws IOException {
        String pattern = (String) req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = (String) req.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String objectKey = new AntPathMatcher().extractPathWithinPattern(pattern, path);
        if (objectKey == null || objectKey.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        boolean asDownload = (download != null && download == 1);
        return files.streamOut(objectKey, range, asDownload);
    }

    @GetMapping("/download/out/**")
    public ResponseEntity<FileSystemResource> downloadOut(HttpServletRequest req) throws IOException {
        String objectKey = tail(req, "/v1/files/download/out/");
        return files.downloadOut(objectKey);
    }

    // ===== util =====
    private static String tail(HttpServletRequest req, String prefix) {
        String uri = URLDecoder.decode(req.getRequestURI(), StandardCharsets.UTF_8);
        int i = uri.indexOf(prefix);
        if (i < 0) throw new IllegalArgumentException("Bad path");
        return uri.substring(i + prefix.length());
    }
}
