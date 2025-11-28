package com.example.clipbot_backend.ffmpeg;

import com.example.clipbot_backend.dto.render.SubtitleStyle;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AssStyleUtil {
    private static final Pattern RGBA = Pattern.compile("rgba\\((\\d+),(\\d+),(\\d+),(\\d+(?:\\.\\d+)?)\\)");

    private AssStyleUtil() {
    }

    public static String toAssColor(String color) {
        color = color.trim().toLowerCase(Locale.ROOT);
        int r = 255;
        int g = 255;
        int b = 255;
        int a = 0;

        if (color.startsWith("#") && (color.length() == 7 || color.length() == 4)) {
            if (color.length() == 7) {
                r = Integer.parseInt(color.substring(1, 3), 16);
                g = Integer.parseInt(color.substring(3, 5), 16);
                b = Integer.parseInt(color.substring(5, 7), 16);
            } else {
                r = Integer.parseInt(color.substring(1, 2) + color.substring(1, 2), 16);
                g = Integer.parseInt(color.substring(2, 3) + color.substring(2, 3), 16);
                b = Integer.parseInt(color.substring(3, 4) + color.substring(3, 4), 16);
            }
        } else {
            Matcher m = RGBA.matcher(color.replace(" ", ""));
            if (m.matches()) {
                r = clamp(Integer.parseInt(m.group(1)));
                g = clamp(Integer.parseInt(m.group(2)));
                b = clamp(Integer.parseInt(m.group(3)));
                double alpha = Double.parseDouble(m.group(4));
                a = (int) Math.round(alpha * 255.0);
            }
        }
        return String.format("&H%02X%02X%02X%02X", a, b, g, r);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public static String buildForceStyle(SubtitleStyle style) {
        SubtitleStyle effective = style == null ? SubtitleStyle.defaults() : style;
        String align = switch (effective.alignment()) {
            case "left" -> "1";
            case "right" -> "3";
            default -> "2";
        };
        return String.join(",",
                "FontName=" + effective.fontFamily(),
                "FontSize=" + effective.fontSize(),
                "PrimaryColour=" + toAssColor(effective.primaryColor()),
                "OutlineColour=" + toAssColor(effective.outlineColor()),
                "BorderStyle=3",
                "Outline=" + effective.outline(),
                "Shadow=" + effective.shadow(),
                "MarginL=" + effective.marginL(),
                "MarginR=" + effective.marginR(),
                "MarginV=" + effective.marginV(),
                "Alignment=" + align,
                "WrapStyle=" + effective.wrapStyle()
        );
    }
}
