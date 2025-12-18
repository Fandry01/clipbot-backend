package com.example.clipbot_backend.service.thumbnail;

import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Project;
import com.example.clipbot_backend.model.ProjectMediaLink;
import com.example.clipbot_backend.repository.AccountRepository;
import com.example.clipbot_backend.repository.AssetRepository;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.ProjectMediaRepository;
import com.example.clipbot_backend.repository.ProjectRepository;
import com.example.clipbot_backend.service.LocalStorageService;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.example.clipbot_backend.util.AssetKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ThumbnailServiceExtractionTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMediaRepository projectMediaRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private AccountRepository accountRepository;

    @TempDir
    Path tmp;

    private StorageService storageService;
    private ThumbnailService thumbnailService;

    @BeforeEach
    void setUp() {
        storageService = new LocalStorageService(tmp, "raw", "out");
        thumbnailService = new ThumbnailService(
                projectRepository,
                projectMediaRepository,
                assetRepository,
                mediaRepository,
                accountRepository,
                storageService,
                "ffmpeg"
        );
    }

    @Test
    void extractsThumbnailWhenLocalVideoExists() throws Exception {
        UUID mediaId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Media media = new Media();
        media.setId(mediaId);
        media.setDurationMs(12_000L);
        media.setObjectKey("ext/yt/sample/source.mp4");
        Account owner = new Account();
        owner.setId(ownerId);
        media.setOwner(owner);

        Path rawPath = storageService.resolveRaw(media.getObjectKey());
        Files.createDirectories(rawPath.getParent());

        Process ffmpeg = new ProcessBuilder(
                "ffmpeg", "-y",
                "-f", "lavfi",
                "-i", "color=c=red:s=160x120:d=2",
                "-pix_fmt", "yuv420p",
                rawPath.toString()
        ).redirectErrorStream(true).start();
        assumeTrue(ffmpeg.waitFor() == 0, "ffmpeg unavailable");

        Project project = new Project(owner, "demo", null, null);
        project.setId(UUID.randomUUID());
        ProjectMediaLink link = new ProjectMediaLink(project, media);

        when(mediaRepository.getReferenceById(mediaId)).thenReturn(media);
        when(accountRepository.getReferenceById(ownerId)).thenReturn(owner);
        when(projectMediaRepository.findByMedia(any(Media.class))).thenReturn(List.of(link));

        thumbnailService.extractFromLocalMedia(media, rawPath);

        String expectedThumbKey = String.format("media/thumbs/%s.jpg", mediaId);
        assertThat(storageService.existsInOut(expectedThumbKey)).isTrue();

        ArgumentCaptor<com.example.clipbot_backend.model.Asset> assetCaptor = ArgumentCaptor.forClass(com.example.clipbot_backend.model.Asset.class);
        verify(assetRepository).save(assetCaptor.capture());
        assertThat(assetCaptor.getValue().getKind()).isEqualTo(AssetKind.THUMBNAIL);
        assertThat(assetCaptor.getValue().getObjectKey()).isEqualTo(expectedThumbKey);

        ArgumentCaptor<List<Project>> projectsCaptor = ArgumentCaptor.forClass(List.class);
        verify(projectRepository).saveAll(projectsCaptor.capture());
        assertThat(projectsCaptor.getValue()).hasSize(1);
        assertThat(projectsCaptor.getValue().get(0).getThumbnailUrl()).isEqualTo(expectedThumbKey);
    }
}
