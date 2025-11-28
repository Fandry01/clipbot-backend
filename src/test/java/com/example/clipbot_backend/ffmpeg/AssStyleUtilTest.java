package com.example.clipbot_backend.ffmpeg;

import com.example.clipbot_backend.dto.render.SubtitleStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssStyleUtilTest {

    @Test
    void toAssColorConvertsHexWhite() {
        assertThat(AssStyleUtil.toAssColor("#FFFFFF")).isEqualTo("&H00FFFFFF");
    }

    @Test
    void toAssColorConvertsRgbaWithAlpha() {
        assertThat(AssStyleUtil.toAssColor("rgba(0,0,0,0.5)")).isEqualTo("&H80000000");
    }

    @Test
    void buildForceStyleUsesDefaultsWhenNull() {
        String forceStyle = AssStyleUtil.buildForceStyle(null);
        SubtitleStyle defaults = SubtitleStyle.defaults();
        assertThat(forceStyle).contains("FontName=" + defaults.fontFamily());
        assertThat(forceStyle).contains("WrapStyle=" + defaults.wrapStyle());
    }
}
