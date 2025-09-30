package com.example.clipbot_backend.dto.web;

import java.util.Map;

public record TranscriptionResult(String lang, String provider, String text, Map<String,Object> words) {
}
