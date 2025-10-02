package com.example.clipbot_backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RenderSpec(@Min(144) @Max(7680) Integer width,
                         @Min(144) @Max(4320)Integer height,
                         @Min(1) @Max(60)Integer fps,
                         @Min(1) @Max(51)Integer crf,
                         String preset,
                         String profile) {
}
