package com.example.clipbot_backend.dto.web;

public record RunNowRequest(String lang, String provider, Integer maxCandidates, Double sceneThreshold) {
}
