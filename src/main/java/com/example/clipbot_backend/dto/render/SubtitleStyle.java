package com.example.clipbot_backend.dto.render;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SubtitleStyle(
        @NotBlank String fontFamily,
        @Min(8) @Max(96) Integer fontSize,
        @NotBlank String primaryColor,
        @NotBlank String outlineColor,
        @Min(0) @Max(8) Integer outline,
        @Min(0) @Max(12) Integer shadow,
        @Pattern(regexp = "left|center|right") String alignment,
        @Min(0) @Max(400) Integer marginL,
        @Min(0) @Max(400) Integer marginR,
        @Min(0) @Max(400) Integer marginV,
        @Min(0) @Max(3) Integer wrapStyle,

        // ✅ NEW
        Boolean backgroundBar,
        String backgroundColor
) {
    public static SubtitleStyle defaults() {
        return new SubtitleStyle(
                // kies iets dat je render container echt heeft (Roboto of Montserrat)
                "Roboto",
                42,                       // 9:16 base (scaled later for 1080h)
                "#FFFFFF",
                "#000000",
                2,
                0,
                "center",
                60,
                60,
                90,
                2,

                false,
                "rgba(0,0,0,0.55)"
        );
    }

    public boolean bgEnabled() {
        return backgroundBar != null && backgroundBar;
    }

    public String bgColorOrDefault() {
        return (backgroundColor == null || backgroundColor.isBlank())
                ? "rgba(0,0,0,0.55)"
                : backgroundColor;
    }
}
