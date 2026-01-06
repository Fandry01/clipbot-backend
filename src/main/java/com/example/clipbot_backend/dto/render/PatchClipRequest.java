package com.example.clipbot_backend.dto.render;

import java.util.Map;

public record PatchClipRequest(String title,
                               Long startMs,
                               Long endMs,
                               Map<String, Object> meta,              // optioneel: “losse” meta keys
                               SubtitleStyle subtitleStyle) {
}
