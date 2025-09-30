package com.example.clipbot_backend.dto.web;

import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record ClipCustomRequest(@NotNull UUID mediaId, @NotNull Long startMs, @NotNull Long endMs, String title, Map<String, Object> meta) {
}
