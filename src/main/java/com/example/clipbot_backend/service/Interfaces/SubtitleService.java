package com.example.clipbot_backend.service.Interfaces;

import com.example.clipbot_backend.dto.SubtitleFiles;
import com.example.clipbot_backend.model.Transcript;


public interface SubtitleService {
    SubtitleFiles buildSubtitles(Transcript t, long startMs, long endMs);
}
