package com.example.clipbot_backend.service.thumbnail;

import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Asset;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Project;
import com.example.clipbot_backend.repository.AccountRepository;
import com.example.clipbot_backend.repository.AssetRepository;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.ProjectMediaRepository;
import com.example.clipbot_backend.repository.ProjectRepository;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.example.clipbot_backend.util.AssetKind;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages project thumbnails, allowing uploads to defer thumbnail assignment until the first render completes.
 */
@Service
public class ThumbnailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThumbnailService.class);
    private static final String DEFAULT_THUMB_PATTERN = "media/thumbs/%s.jpg";
    private static final double MIN_THUMB_SEC = 1.0;
    private static final double DEFAULT_THUMB_SEC = 5.0;
    private static final double PCT_THUMB_FRACTION = 0.04;
    private static final double MIN_PCT_SEC = 3.0;

    private final ProjectRepository projectRepository;
    private final ProjectMediaRepository projectMediaRepository;
    private final AssetRepository assetRepository;
    private final MediaRepository mediaRepository;
    private final AccountRepository accountRepository;
    private final StorageService storageService;
    private final String ffmpegBin;
    private final Set<UUID> pendingFallbacks = ConcurrentHashMap.newKeySet();

    public ThumbnailService(ProjectRepository projectRepository,
                            ProjectMediaRepository projectMediaRepository,
                            AssetRepository assetRepository,
                            MediaRepository mediaRepository,
                            AccountRepository accountRepository,
                            StorageService storageService,
                            @Value("${ffmpeg.binary:ffmpeg}") String ffmpegBin) {
        this.projectRepository = projectRepository;
        this.projectMediaRepository = projectMediaRepository;
        this.assetRepository = assetRepository;
        this.mediaRepository = mediaRepository;
        this.accountRepository = accountRepository;
        this.storageService = storageService;
        this.ffmpegBin = ffmpegBin;
    }

    /**
     * Registers a project for deferred thumbnail assignment when the first clip render finishes.
     *
     * @param projectId project identifier to mark.
     */
    public void registerFirstRenderFallback(UUID projectId) {
        if (projectId == null) {
            return;
        }
        pendingFallbacks.add(projectId);
        LOGGER.info("ThumbnailService fallback registered projectId={}", projectId);
    }

    /**
     * Applies a thumbnail to the project if it is awaiting a fallback and currently has no thumbnail.
     *
     * @param project      project entity to mutate when needed.
     * @param thumbnailUrl thumbnail URL or object key produced by the render flow.
     */
    @Transactional
    public void applyFallbackIfEligible(Project project, String thumbnailUrl) {
        if (project == null || thumbnailUrl == null || thumbnailUrl.isBlank()) {
            return;
        }
        if (!pendingFallbacks.contains(project.getId())) {
            return;
        }
        if (project.getThumbnailUrl() != null && !project.getThumbnailUrl().isBlank()) {
            pendingFallbacks.remove(project.getId());
            return;
        }
        project.setThumbnailUrl(thumbnailUrl);
        projectRepository.save(project);
        pendingFallbacks.remove(project.getId());
        LOGGER.info("ThumbnailService fallback applied projectId={} thumbnail={}", project.getId(), thumbnailUrl);
    }

    /**
     * Extracts a thumbnail from the downloaded source and assigns it to related projects/media.
     * Runs only when the source file is present locally.
     *
     * @param request immutable set of identifiers required for persistence.
     * @param localSource local file to extract from.
     */
    public void extractFromLocalMedia(ThumbnailRequest request, Path localSource) {
        if (request == null || request.mediaId() == null || request.ownerId() == null || localSource == null) {
            return;
        }
        if (!Files.exists(localSource)) {
            LOGGER.debug("Thumbnail extract skipped; file missing mediaId={} path={}", request.mediaId(), localSource);
            return;
        }
        if (!looksLikeVideo(localSource)) {
            LOGGER.debug("Thumbnail extract skipped; non-video source mediaId={} path={}", request.mediaId(), localSource);
            return;
        }

        String thumbKey = buildThumbKey(request.mediaId());
        if (storageService.existsInOut(thumbKey)) {
            persistThumbnailReferences(request.mediaId(), request.ownerId(), request.projectIds(), thumbKey, resolveSize(thumbKey));
            return;
        }

        LOGGER.info("Thumbnail extract scheduled mediaId={} file={} projects={}", request.mediaId(), localSource, request.projectIds());
        Path tempOutput = null;
        try {
            tempOutput = Files.createTempFile("thumb-", ".jpg");
            double seekSec = computeSeekSeconds(request.durationMs());
            List<String> cmd = List.of(
                    ffmpegBin, "-y",
                    "-ss", String.format(java.util.Locale.ROOT, "%.3f", seekSec),
                    "-i", localSource.toAbsolutePath().toString(),
                    "-frames:v", "1",
                    "-q:v", "2",
                    tempOutput.toAbsolutePath().toString()
            );
            LOGGER.info("Thumbnail extract started mediaId={} seekSec={} cmd={} output={} ", request.mediaId(), seekSec, cmd, tempOutput);
            new ProcessBuilder(cmd).redirectErrorStream(true).start().waitFor();

            if (!Files.exists(tempOutput) || Files.size(tempOutput) <= 0) {
                LOGGER.warn("Thumbnail extract failed (empty output) mediaId={} file={}", request.mediaId(), localSource);
                return;
            }

            storageService.uploadToOut(tempOutput, thumbKey);
            long size = Files.size(tempOutput);
            LOGGER.info("Thumbnail extract completed mediaId={} thumbKey={} size={}B", request.mediaId(), thumbKey, size);
            persistThumbnailReferences(request.mediaId(), request.ownerId(), request.projectIds(), thumbKey, size);
        } catch (Exception ex) {
            LOGGER.warn("Thumbnail extract failed mediaId={} path={} err={}", request.mediaId(), localSource, ex.toString());
        } finally {
            if (tempOutput != null) {
                try {
                    Files.deleteIfExists(tempOutput);
                } catch (Exception ignore) {
                }
            }
        }
    }

    private long resolveSize(String thumbKey) {
        try {
            return Files.size(storageService.resolveOut(thumbKey));
        } catch (Exception ex) {
            return 0;
        }
    }

    private boolean looksLikeVideo(Path source) {
        String name = source.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".mp4") || name.endsWith(".mov") || name.endsWith(".mkv") || name.endsWith(".webm") || name.endsWith(".m4v");
    }

    private String buildThumbKey(UUID mediaId) {
        return String.format(DEFAULT_THUMB_PATTERN, mediaId);
    }

    private double computeSeekSeconds(Long durationMs) {
        if (durationMs == null || durationMs <= 0) {
            return DEFAULT_THUMB_SEC;
        }
        double durSec = durationMs / 1000.0;
        double pct = durSec * PCT_THUMB_FRACTION;
        double clamped = Math.max(MIN_PCT_SEC, Math.min(pct, Math.max(MIN_THUMB_SEC, durSec - 1.0)));
        return Math.max(MIN_THUMB_SEC, clamped);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void persistThumbnailReferences(UUID mediaId, UUID ownerId, List<UUID> projectIds, String thumbKey, long size) {
        Media mediaRef = mediaRepository.getReferenceById(mediaId);
        Account ownerRef = accountRepository.getReferenceById(ownerId);

        Asset asset = new Asset(ownerRef, AssetKind.THUMBNAIL, thumbKey, size <= 0 ? 1 : size);
        asset.setRelatedMedia(mediaRef);
        assetRepository.save(asset);

        List<UUID> resolvedProjectIds = projectIds;
        if (resolvedProjectIds == null || resolvedProjectIds.isEmpty()) {
            List<UUID> fromRepo = projectMediaRepository.findProjectIdsByMediaId(mediaId);
            resolvedProjectIds = fromRepo == null ? List.of() : fromRepo;
        }
        List<Project> toSave = new ArrayList<>(resolvedProjectIds.size());
        for (UUID projectId : resolvedProjectIds) {
            Project project = projectRepository.getReferenceById(projectId);
            if (project.getThumbnailUrl() == null || project.getThumbnailUrl().isBlank()) {
                project.setThumbnailUrl(thumbKey);
                toSave.add(project);
            }
        }
        if (!toSave.isEmpty()) {
            projectRepository.saveAll(toSave);
        }
        LOGGER.info("ThumbnailService persisted media={} owner={} projectsUpdated={}", mediaId, ownerId, resolvedProjectIds.size());
    }

    /**
     * Immutable DTO for thumbnail extraction without lazy entity dependencies.
     */
    public record ThumbnailRequest(UUID mediaId, UUID ownerId, List<UUID> projectIds, Long durationMs) { }
}
