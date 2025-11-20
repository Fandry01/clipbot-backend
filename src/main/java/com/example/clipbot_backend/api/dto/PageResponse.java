package com.example.clipbot_backend.api.dto;

import java.util.List;

/**
 * Stable pagination DTO to avoid exposing Spring Data internals in JSON.
 */
public record PageResponse<T>(List<T> content, int page, int size, long total) {
}

