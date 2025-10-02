package com.example.clipbot_backend.dto;

import java.util.Map;

public record RenderOptions(RenderSpec spec,
                            Map<String,Object> meta,
                            SubtitleFiles subtitles) {
}
