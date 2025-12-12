package com.example.clipbot_backend.service;

import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Asset;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.repository.AccountRepository;
import com.example.clipbot_backend.repository.AssetRepository;
import com.example.clipbot_backend.repository.JobRepository;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.example.clipbot_backend.util.MediaStatus;
import com.example.clipbot_backend.util.SpeakerMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @Mock
    private StorageService storageService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobService jobService;

    @Test
    void uploadLocalDefaultsToSingleSpeakerWhenFlagFalse() throws Exception {
        UploadService uploadService = new UploadService(
                storageService,
                accountRepository,
                mediaRepository,
                assetRepository,
                jobRepository,
                jobService
        );

        Account owner = new Account("owner", "owner");
        when(accountRepository.findByExternalSubject("owner")).thenReturn(Optional.of(owner));
        when(mediaRepository.saveAndFlush(any(Media.class))).thenAnswer(invocation -> {
            Media media = invocation.getArgument(0, Media.class);
            media.setId(UUID.randomUUID());
            return media;
        });
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0, Media.class));
        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> {
            Asset asset = invocation.getArgument(0, Asset.class);
            asset.setId(UUID.randomUUID());
            return asset;
        });
        doNothing().when(storageService).uploadToRaw(any(), any());
        doNothing().when(jobService).enqueue(any(), any(), any());

        MockMultipartFile file = new MockMultipartFile("file", "sample.mp3", "audio/mpeg", "data".getBytes());

        uploadService.uploadLocal("owner", null, file, "upload", false);

        ArgumentCaptor<Media> mediaCaptor = ArgumentCaptor.forClass(Media.class);
        verify(mediaRepository).saveAndFlush(mediaCaptor.capture());
        verify(mediaRepository).save(mediaCaptor.capture());

        assertThat(mediaCaptor.getAllValues())
                .allMatch(media -> media.getSpeakerMode() == SpeakerMode.SINGLE);
        assertThat(mediaCaptor.getValue().getStatus()).isEqualTo(MediaStatus.UPLOADED);
    }

    @Test
    void uploadLocalSetsMultiSpeakerWhenFlagTrue() throws Exception {
        UploadService uploadService = new UploadService(
                storageService,
                accountRepository,
                mediaRepository,
                assetRepository,
                jobRepository,
                jobService
        );

        Account owner = new Account("owner", "owner");
        when(accountRepository.findByExternalSubject("owner")).thenReturn(Optional.of(owner));
        when(mediaRepository.saveAndFlush(any(Media.class))).thenAnswer(invocation -> {
            Media media = invocation.getArgument(0, Media.class);
            media.setId(UUID.randomUUID());
            return media;
        });
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0, Media.class));
        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> {
            Asset asset = invocation.getArgument(0, Asset.class);
            asset.setId(UUID.randomUUID());
            return asset;
        });
        doNothing().when(storageService).uploadToRaw(any(), any());
        doNothing().when(jobService).enqueue(any(), any(), any());

        MockMultipartFile file = new MockMultipartFile("file", "sample.mp3", "audio/mpeg", "data".getBytes());

        uploadService.uploadLocal("owner", null, file, "upload", true);

        ArgumentCaptor<Media> mediaCaptor = ArgumentCaptor.forClass(Media.class);
        verify(mediaRepository).saveAndFlush(mediaCaptor.capture());
        verify(mediaRepository).save(mediaCaptor.capture());

        assertThat(mediaCaptor.getAllValues())
                .allMatch(media -> media.getSpeakerMode() == SpeakerMode.MULTI);
        assertThat(mediaCaptor.getValue().getStatus()).isEqualTo(MediaStatus.UPLOADED);
    }
}

