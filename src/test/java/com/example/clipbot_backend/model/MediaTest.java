package com.example.clipbot_backend.model;

import com.example.clipbot_backend.util.SpeakerMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MediaTest {

    @Test
    void legacyAutoSpeakerModeIsTreatedAsSingle() {
        Media media = new Media();
        media.setSpeakerMode(SpeakerMode.AUTO);

        assertThat(media.isMultiSpeakerEffective()).isFalse();
    }
}

