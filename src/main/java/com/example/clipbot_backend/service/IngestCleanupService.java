package com.example.clipbot_backend.service;

import com.example.clipbot_backend.config.IngestCleanupProperties;
import com.example.clipbot_backend.model.Asset;
import com.example.clipbot_backend.model.Clip;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.model.Project;
import com.example.clipbot_backend.model.ProjectMediaLink;
import com.example.clipbot_backend.repository.AssetRepository;
import com.example.clipbot_backend.repository.ClipRepository;
import com.example.clipbot_backend.repository.JobRepository;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.repository.ProjectMediaRepository;
import com.example.clipbot_backend.repository.ProjectRepository;
import com.example.clipbot_backend.repository.SegmentRepository;
import com.example.clipbot_backend.repository.TranscriptRepository;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestCleanupService {
    private static final Logger LOGGER = LoggerFactory.getLogger(IngestCleanupService.class);

    private final JobRepository jobRepository;
    private final ClipRepository clipRepository;
    private final AssetRepository assetRepository;
    private final SegmentRepository segmentRepository;
    private final TranscriptRepository transcriptRepository;
    private final ProjectMediaRepository projectMediaRepository;
    private final ProjectRepository projectRepository;
    private final MediaRepository mediaRepository;
    private final StorageService storageService;
    private final IngestCleanupProperties properties;
    private final ObjectMapper objectMapper;

    public IngestCleanupService(JobRepository jobRepository,
                                ClipRepository clipRepository,
                                AssetRepository assetRepository,
                                SegmentRepository segmentRepository,
                                TranscriptRepository transcriptRepository,
                                ProjectMediaRepository projectMediaRepository,
                                ProjectRepository projectRepository,
                                MediaRepository mediaRepository,
                                StorageService storageService,
                                IngestCleanupProperties properties,
                                ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.clipRepository = clipRepository;
        this.assetRepository = assetRepository;
        this.segmentRepository = segmentRepository;
        this.transcriptRepository = transcriptRepository;
        this.projectMediaRepository = projectMediaRepository;
        this.projectRepository = projectRepository;
        this.mediaRepository = mediaRepository;
        this.storageService = storageService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Cancels queued/running jobs for a media item and removes database records and files created during ingest.
     *
     * @param mediaId     media identifier to clean up; ignored when {@code null}.
     * @param jobId       current job id to exclude from mass-failure updates (may be {@code null}).
     * @param objectKey   raw storage object key, used as fallback when the media record is missing.
     * @param externalUrl original ingest URL for logging purposes.
     * @param cause       failure that triggered the cleanup (logged in job results and warnings).
     */
    public void cleanupFailedIngest(UUID mediaId, UUID jobId, String objectKey, String externalUrl, Throwable cause) {
        LOGGER.warn("INGEST CLEANUP start mediaId={} jobId={} objectKey={} url={} reason={}", mediaId, jobId, objectKey, externalUrl, cause == null ? null : cause.toString());
        CleanupPlan plan = cleanupEntities(mediaId, jobId, cause);
        if (properties.isDeleteFiles()) {
            cleanupFiles(plan, objectKey);
        }
        LOGGER.info("INGEST CLEANUP done mediaId={} jobId={} raw={} outKeys={}", mediaId, jobId, plan.rawKey(), plan.outKeys().size());
    }

    @Transactional
    CleanupPlan cleanupEntities(UUID mediaId, UUID jobId, Throwable cause) {
        Media media = mediaId != null ? mediaRepository.findById(mediaId).orElse(null) : null;
        if (mediaId != null) {
            jobRepository.failQueuedAndRunningByMedia(mediaId, jobId, failureJson(cause));
        }
        if (media == null) {
            return new CleanupPlan(null, List.of());
        }

        List<Clip> clips = clipRepository.findByMedia(media);
        List<Asset> clipAssets = clips.isEmpty() ? List.of() : assetRepository.findByRelatedClipIn(clips);
        if (!clipAssets.isEmpty()) {
            assetRepository.deleteByRelatedClipIn(clips);
        }
        if (!clips.isEmpty()) {
            clipRepository.deleteAll(clips);
        }

        List<Asset> mediaAssets = assetRepository.findByRelatedMedia(media);
        if (!mediaAssets.isEmpty()) {
            assetRepository.deleteAll(mediaAssets);
        }

        segmentRepository.deleteByMedia(media);
        transcriptRepository.deleteByMedia(media);

        List<ProjectMediaLink> links = projectMediaRepository.findByMedia(media);
        if (!links.isEmpty()) {
            projectMediaRepository.deleteAll(links);
        }
        if (properties.isDeleteProjectOnDownloadFail()) {
            deleteEmptyProjects(links);
        }

        mediaRepository.delete(media);

        Set<String> outKeys = new HashSet<>();
        clipAssets.forEach(asset -> outKeys.add(asset.getObjectKey()));
        mediaAssets.forEach(asset -> outKeys.add(asset.getObjectKey()));

        return new CleanupPlan(media.getObjectKey(), new ArrayList<>(outKeys));
    }

    private void deleteEmptyProjects(List<ProjectMediaLink> links) {
        if (links == null || links.isEmpty()) {
            return;
        }
        for (ProjectMediaLink link : links) {
            Project project = link.getProject();
            if (project == null) {
                continue;
            }
            boolean hasMedia = !projectMediaRepository.findByProject(project).isEmpty();
            if (!hasMedia) {
                projectRepository.delete(project);
            }
        }
    }

    private void cleanupFiles(CleanupPlan plan, String fallbackKey) {
        String rawKey = plan.rawKey() != null ? plan.rawKey() : objectKeyOrNull(fallbackKey);
        if (rawKey != null) {
            deletePathAndParents(storageService.resolveRaw(rawKey), storageService.rootRaw());
            deletePathAndParents(storageService.resolveOut(rawKey), storageService.rootOut());
        }

        for (String outKey : plan.outKeys()) {
            deletePathAndParents(storageService.resolveOut(outKey), storageService.rootOut());
        }
    }

    private void deletePathAndParents(Path path, Path root) {
        if (path == null) {
            return;
        }
        safeDelete(path);
        Path parent = path.getParent();
        while (parent != null && root != null && !parent.equals(root) && parent.startsWith(root)) {
            if (!isDirectoryEmpty(parent)) {
                break;
            }
            safeDelete(parent);
            parent = parent.getParent();
        }
    }

    private void safeDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            LOGGER.warn("Cleanup delete failed path={} err={}", path, e.toString());
        }
    }

    private boolean isDirectoryEmpty(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream.findFirst().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private String failureJson(Throwable cause) {
        try {
            return objectMapper.writeValueAsString(
                    java.util.Map.of(
                            "error", "INGEST_FAILED",
                            "reason", cause == null ? null : cause.getMessage())
            );
        } catch (Exception e) {
            return "{\"error\":\"INGEST_FAILED\"}";
        }
    }

    private String objectKeyOrNull(String objectKey) {
        return (objectKey == null || objectKey.isBlank()) ? null : objectKey;
    }

    record CleanupPlan(String rawKey, List<String> outKeys) {}
}
