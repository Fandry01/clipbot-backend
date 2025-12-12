package com.example.clipbot_backend.service;

import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.util.MediaPlatform;
import com.example.clipbot_backend.util.MediaStatus;
import com.example.clipbot_backend.util.SpeakerMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private AccountService accountService;

    @Test
    void createFromUrlDefaultsToSingleSpeakerWhenFlagOmitted() {
        MediaService service = new MediaService(mediaRepository, accountService);
        UUID ownerId = UUID.randomUUID();
        Account owner = new Account("owner", "owner");
        when(accountService.getByIdOrThrow(ownerId)).thenReturn(owner);
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> {
            Media media = invocation.getArgument(0, Media.class);
            media.setId(UUID.randomUUID());
            return media;
        });

        UUID mediaId = service.createMediaFromUrl(
                ownerId,
                "https://example.com/audio.mp3",
                MediaPlatform.OTHER,
                "url",
                1_000L,
                null,
                null
        );

        ArgumentCaptor<Media> saved = ArgumentCaptor.forClass(Media.class);
        verify(mediaRepository).save(saved.capture());
        Media persisted = saved.getValue();

        assertThat(mediaId).isNotNull();
        assertThat(persisted.getSpeakerMode()).isEqualTo(SpeakerMode.SINGLE);
        assertThat(persisted.getStatus()).isEqualTo(MediaStatus.DOWNLOADING);
    }

    @Test
    void createFromUrlUsesMultiSpeakerWhenRequested() {
        MediaService service = new MediaService(mediaRepository, accountService);
        UUID ownerId = UUID.randomUUID();
        Account owner = new Account("owner", "owner");
        when(accountService.getByIdOrThrow(ownerId)).thenReturn(owner);
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> {
            Media media = invocation.getArgument(0, Media.class);
            media.setId(UUID.randomUUID());
            return media;
        });

        UUID mediaId = service.createMediaFromUrl(
                ownerId,
                "https://example.com/audio.mp3",
                MediaPlatform.OTHER,
                "url",
                1_000L,
                null,
                SpeakerMode.MULTI
        );

        ArgumentCaptor<Media> saved = ArgumentCaptor.forClass(Media.class);
        verify(mediaRepository).save(saved.capture());
        Media persisted = saved.getValue();

        assertThat(mediaId).isNotNull();
        assertThat(persisted.getSpeakerMode()).isEqualTo(SpeakerMode.MULTI);
    }
}
