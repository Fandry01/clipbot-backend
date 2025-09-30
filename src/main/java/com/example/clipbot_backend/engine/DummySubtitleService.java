package com.example.clipbot_backend.engine;

import com.example.clipbot_backend.dto.RenderResult;
import com.example.clipbot_backend.dto.SubtitleFiles;
import com.example.clipbot_backend.model.Transcript;
import com.example.clipbot_backend.service.StorageService;
import com.example.clipbot_backend.service.SubtitleService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

@Service
public class DummySubtitleService implements SubtitleService {
    private final StorageService storageService;
    public DummySubtitleService(StorageService storageService) {
        this.storageService = storageService;
    }

    public SubtitleFiles buildSubtitles(Transcript transcript, long startMs, long endMs){
        // demo maakt alleen srt

        try{
            String srtKey = "subs/" + UUID.randomUUID() + ".srt";
            var tmp = Files.createTempFile("clip-", "mp4");
            Files.writeString(tmp, "1\n00:00:00,000 --> 00:00:01,000\nHello\n");
            storageService.uploadToOut(tmp, srtKey);
            long srtSize = Files.size(tmp);
            return new SubtitleFiles(srtKey, srtSize, null, 0);
        } catch (Exception e) {
            return new SubtitleFiles(null, 0, null,0);
        }
    }
}
