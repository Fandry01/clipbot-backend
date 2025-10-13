package com.example.clipbot_backend.dto.web;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetadataResponse(
        String platform,
        String url,
        String title,
        String author,
        Integer durationSec,
        String thumbnail
) {
}
