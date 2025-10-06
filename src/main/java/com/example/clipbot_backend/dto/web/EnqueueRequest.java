package com.example.clipbot_backend.dto.web;

public record EnqueueRequest(String lang, String provider, Double sceneThreshold) {
}
