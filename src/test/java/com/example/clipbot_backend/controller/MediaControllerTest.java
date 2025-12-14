package com.example.clipbot_backend.controller;

import com.example.clipbot_backend.dto.web.MediaFromUrlRequest;
import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.service.MediaService;
import com.example.clipbot_backend.service.metadata.MetadataService;
import com.example.clipbot_backend.util.MediaPlatform;
import com.example.clipbot_backend.util.MediaStatus;
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
class MediaControllerTest {

    @Mock
    private MediaService mediaService;

    @Mock
    private MetadataService metadataService;

    @Test
    void nonUuidOwnerIdFallsBackToExternalSubject() {
        MediaController controller = new MediaController(mediaService, metadataService);
        MediaFromUrlRequest request = new MediaFromUrlRequest(
                "demo-user-1",
                null,
                "https://example.com/audio.mp3",
                null,
                null,
                false
        );

        when(metadataService.resolve(any(String.class))).thenReturn(null);
        when(metadataService.normalizeUrl(any(String.class))).thenReturn("https://example.com/audio.mp3");
        when(metadataService.detectPlatform(any(String.class))).thenReturn(MediaPlatform.OTHER);

        UUID mediaId = UUID.randomUUID();
        when(mediaService.createMediaFromUrl(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mediaId);

        Account owner = new Account("demo-user-1", "demo-user-1");
        Media media = new Media();
        media.setId(mediaId);
        media.setOwner(owner);
        media.setObjectKey("ext/url/source.m4a");
        media.setStatus(MediaStatus.DOWNLOADING);
        when(mediaService.get(mediaId)).thenReturn(media);

        controller.createFromUrl(request);

        ArgumentCaptor<UUID> ownerId = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> externalSubject = ArgumentCaptor.forClass(String.class);
        verify(mediaService).createMediaFromUrl(
                ownerId.capture(),
                externalSubject.capture(),
                any(), any(), any(), any(), any(), any()
        );

        assertThat(ownerId.getValue()).isNull();
        assertThat(externalSubject.getValue()).isEqualTo("demo-user-1");
    }
}
