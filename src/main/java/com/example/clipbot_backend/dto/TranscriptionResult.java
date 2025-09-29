package com.example.clipbot_backend.dto;

import java.util.Map;

public record TranscriptionResult(String lang, String provider, String text, Map<String,Object> words) {
}
