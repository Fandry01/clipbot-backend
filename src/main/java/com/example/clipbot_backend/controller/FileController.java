package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.service.Interfaces.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;

@RestController
@RequestMapping("/v1/files")
public class FileController {
    private final StorageService storage;

    public FileController(StorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/out/**")
    public ResponseEntity<FileSystemResource> getOut(HttpServletRequest req) throws IOException {
        String prefix = "/v1/files/out/";
        String uri = req.getRequestURI();
        String objectKey = uri.substring(uri.indexOf(prefix) + prefix.length());
        var file = storage.resolveOut(objectKey);
        if (!Files.exists(file)) return ResponseEntity.notFound().build();
        var resource = new FileSystemResource(file);
        var probe = Files.probeContentType(file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(probe != null ? probe : "application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + file.getFileName())
                .body(resource);
    }
}