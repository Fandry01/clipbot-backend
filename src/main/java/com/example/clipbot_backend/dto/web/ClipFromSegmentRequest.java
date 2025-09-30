package com.example.clipbot_backend.dto.web;

import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record ClipFromSegmentRequest(@NotNull UUID mediaId, @NotNull UUID segmentId, String title, Map<String, Object> meta) {}
