package com.example.clipbot_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.repository.AccountRepository;
import com.example.clipbot_backend.repository.AssetRepository;
import com.example.clipbot_backend.repository.JobRepository;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.util.MediaPlatform;
import com.example.clipbot_backend.util.SpeakerMode;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MediaSpeakerModeIngestTest {

    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private AccountService accountService;

    @Mock
    private com.example.clipbot_backend.service.Interfaces.StorageService storageService;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private JobRepository jobRepository;
    @Mock
    private JobService jobService;

    private MediaService mediaService;
    private UploadService uploadService;

    @BeforeEach
    void setup() {
        mediaService = new MediaService(mediaRepository, accountService);
        uploadService = new UploadService(storageService, accountRepository, mediaRepository, assetRepository, jobRepository, jobService);
    }

    @Test
    void podcastIntentPersistsMultiSpeakerModeOnUrlIngest() {
        UUID ownerId = UUID.randomUUID();
        Account owner = new Account("ext", "owner");
        ReflectionTestUtils.setField(owner, "id", ownerId);
        when(accountService.getByIdOrThrow(ownerId)).thenReturn(owner);
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> {
            Media m = invocation.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        UUID id = mediaService.createMediaFromUrl(ownerId, "http://example.com", MediaPlatform.OTHER, "url", null, null, SpeakerMode.MULTI);

        ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
        assertThat(id).isNotNull();
        org.mockito.Mockito.verify(mediaRepository).save(captor.capture());
        assertThat(captor.getValue().getSpeakerMode()).isEqualTo(SpeakerMode.MULTI);
    }

    @Test
    void uploadLocalSetsSpeakerModeFromPodcastFlag() throws Exception {
        Account owner = new Account("ext", "owner");
        ReflectionTestUtils.setField(owner, "id", UUID.randomUUID());
        when(accountRepository.findByExternalSubject(anyString())).thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenReturn(owner);

        when(mediaRepository.saveAndFlush(any(Media.class))).thenAnswer(invocation -> {
            Media m = invocation.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(assetRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.resolveRaw(anyString())).thenReturn(Path.of("/tmp"));
        org.mockito.Mockito.doNothing().when(storageService).uploadToRaw(any(Path.class), anyString());
        when(jobService.enqueue(any(), any(), any())).thenReturn(UUID.randomUUID());

        MockMultipartFile file = new MockMultipartFile("file", "sample.mp4", "video/mp4", new byte[]{1, 2, 3});
        uploadService.uploadLocal("ext", null, file, "upload", true);

        ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
        org.mockito.Mockito.verify(mediaRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSpeakerMode()).isEqualTo(SpeakerMode.MULTI);
    }
}
