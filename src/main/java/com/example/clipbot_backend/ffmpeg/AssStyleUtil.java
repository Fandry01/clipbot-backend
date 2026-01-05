package com.example.clipbot_backend.ffmpeg;

import com.example.clipbot_backend.dto.render.SubtitleStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AssStyleUtil {
    private static final Pattern RGBA = Pattern.compile("rgba\\((\\d+),(\\d+),(\\d+),(\\d+(?:\\.\\d+)?)\\)");

    private AssStyleUtil() {}

    /**
     * ASS: &HAABBGGRR
     * Alpha is transparency: 00=opaque, FF=transparent
     */
    public static String toAssColor(String color) {
        if (color == null || color.isBlank()) return "&H00000000";
        color = color.trim().toLowerCase(Locale.ROOT);

        int r = 255, g = 255, b = 255;
        int aAss = 0;

        if (color.startsWith("#")) {
            if (color.length() == 4) { // #RGB
                r = Integer.parseInt(color.substring(1, 2) + color.substring(1, 2), 16);
                g = Integer.parseInt(color.substring(2, 3) + color.substring(2, 3), 16);
                b = Integer.parseInt(color.substring(3, 4) + color.substring(3, 4), 16);
            } else if (color.length() == 7) { // #RRGGBB
                r = Integer.parseInt(color.substring(1, 3), 16);
                g = Integer.parseInt(color.substring(3, 5), 16);
                b = Integer.parseInt(color.substring(5, 7), 16);
            } else if (color.length() == 9) { // #RRGGBBAA (AA = opacity)
                r = Integer.parseInt(color.substring(1, 3), 16);
                g = Integer.parseInt(color.substring(3, 5), 16);
                b = Integer.parseInt(color.substring(5, 7), 16);
                int aCss = Integer.parseInt(color.substring(7, 9), 16);
                aAss = 255 - aCss;
            }
        } else {
            Matcher m = RGBA.matcher(color.replace(" ", ""));
            if (m.matches()) {
                r = clamp(Integer.parseInt(m.group(1)));
                g = clamp(Integer.parseInt(m.group(2)));
                b = clamp(Integer.parseInt(m.group(3)));
                double opacity = Double.parseDouble(m.group(4));
                opacity = Math.max(0.0, Math.min(1.0, opacity));
                aAss = (int) Math.round((1.0 - opacity) * 255.0);
            }
        }

        return String.format("&H%02X%02X%02X%02X", aAss, b, g, r);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** ASS wants one font name, not a CSS stack. */
    private static String assFontName(String fontFamily) {
        if (fontFamily == null || fontFamily.isBlank()) return "Roboto";
        String first = fontFamily.split(",")[0].trim();
        if (first.startsWith("\"") && first.endsWith("\"") && first.length() > 1) {
            first = first.substring(1, first.length() - 1);
        }
        return first;
    }

    /**
     * Build subtitles force_style string, scaled for output height.
     * @param style style (nullable)
     * @param videoH output video height (e.g. 1920 / 1080)
     */
    public static String buildForceStyle(SubtitleStyle style, int videoH) {
        SubtitleStyle effective = style == null ? SubtitleStyle.defaults() : style;

        double k = videoH > 0 ? (videoH / 1920.0) : 1.0;

        int fontSize = (int) Math.round(nvl(effective.fontSize(), 42) * k);
        int outline  = (int) Math.round(nvl(effective.outline(), 2) * k);
        int shadow   = (int) Math.round(nvl(effective.shadow(), 0) * k);
        int mL       = (int) Math.round(nvl(effective.marginL(), 60) * k);
        int mR       = (int) Math.round(nvl(effective.marginR(), 60) * k);
        int mV       = (int) Math.round(nvl(effective.marginV(), 90) * k);

        String align = switch (nvl(effective.alignment(), "center")) {
            case "left" -> "1";
            case "right" -> "3";
            default -> "2";
        };

        boolean bg = effective.bgEnabled();
        String bgColor = effective.bgColorOrDefault();

        int borderStyle = bg ? 3 : 1;

        // als bg aan staat: laat een klein beetje outline staan voor safety
        int out = bg ? Math.max(1, outline) : outline;
        int sh  = bg ? 0 : shadow;

        List<String> parts = new ArrayList<>();
        parts.add("FontName=" + assFontName(effective.fontFamily()));
        parts.add("FontSize=" + fontSize);
        parts.add("PrimaryColour=" + toAssColor(effective.primaryColor()));
        parts.add("OutlineColour=" + toAssColor(effective.outlineColor()));
        if (bg) parts.add("BackColour=" + toAssColor(bgColor));
        parts.add("BorderStyle=" + borderStyle);
        parts.add("Outline=" + out);
        parts.add("Shadow=" + sh);
        parts.add("MarginL=" + mL);
        parts.add("MarginR=" + mR);
        parts.add("MarginV=" + mV);
        parts.add("Alignment=" + align);
        parts.add("WrapStyle=" + nvl(effective.wrapStyle(), 2));

        return String.join(",", parts);
    }


    private static int nvl(Integer v, int d) {
        return v == null ? d : v;
    }

    private static String nvl(String v, String d) {
        return (v == null || v.isBlank()) ? d : v;
    }
}
