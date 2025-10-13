package com.example.clipbot_backend.util;

import java.util.Locale;

/**
 * Normalized set of supported external media platforms.
 */
public enum MediaPlatform {
    YOUTUBE("youtube"),
    VIMEO("vimeo"),
    REDDIT("reddit"),
    X("x"),
    FACEBOOK("facebook"),
    TWITCH("twitch"),
    RUMBLE("rumble"),
    OTHER("other");

    private final String id;

    MediaPlatform(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static MediaPlatform fromId(String id) {
        if (id == null) {
            return OTHER;
        }
        var normalized = id.toLowerCase(Locale.ROOT);
        for (var value : values()) {
            if (value.id.equals(normalized)) {
                return value;
            }
        }
        return OTHER;
    }
}
