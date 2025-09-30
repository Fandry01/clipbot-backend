package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.SubtitleFiles;
import com.example.clipbot_backend.model.Transcript;
import org.springframework.stereotype.Service;


public interface SubtitleService {
    SubtitleFiles buildSubtitles(Transcript t, long startMs, long endMs);
}
