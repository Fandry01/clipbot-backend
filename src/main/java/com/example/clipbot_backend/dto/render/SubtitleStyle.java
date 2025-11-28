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
        @Min(0) @Max(200) Integer marginL,
        @Min(0) @Max(200) Integer marginR,
        @Min(0) @Max(200) Integer marginV,
        @Min(0) @Max(3) Integer wrapStyle
) {
    public static SubtitleStyle defaults() {
        return new SubtitleStyle(
                "Inter Semi Bold", 17, "#FFFFFF", "rgba(0,0,0,0.5)",
                1, 0, "center", 154, 154, 40, 2
        );
    }
}
