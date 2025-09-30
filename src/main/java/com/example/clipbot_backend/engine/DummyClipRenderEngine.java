package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.dto.RenderOptions;
import com.example.clipbot_backend.dto.RenderResult;
import com.example.clipbot_backend.engine.Interfaces.ClipRenderEngine;
import com.example.clipbot_backend.service.StorageService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class DummyClipRenderEngine implements ClipRenderEngine {
    private final StorageService storageService;
    public DummyClipRenderEngine(StorageService storageService) {
        this.storageService = storageService;
    }

    public RenderResult render(Path mediaFile, long startMs, long endMs, RenderOptions renderOptions) throws IOException {
        // simuleer render: schrijf leeg bestand in OUT
        String key = "clips/demo/" + UUID.randomUUID() + ".mp4";
        var tmp = Files.createTempFile("clip-", ".mp4");
        Files.writeString(tmp,"fake mp4");
        storageService.uploadToOut(tmp, key);
        long size = Files.size(tmp);
        Files.delete(tmp);
        return new RenderResult(key, size, null, 0);
    }
}
