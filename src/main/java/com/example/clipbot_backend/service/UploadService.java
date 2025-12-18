package com.example.clipbot_backend.service;

import com.example.clipbot_backend.dto.web.UploadCompleteResponse;
import com.example.clipbot_backend.dto.web.UploadInitRequest;
import com.example.clipbot_backend.dto.web.UploadInitResponse;
import com.example.clipbot_backend.model.Account;
import com.example.clipbot_backend.model.Asset;
import com.example.clipbot_backend.model.Job;
import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.repository.AccountRepository;
import com.example.clipbot_backend.repository.AssetRepository;
import com.example.clipbot_backend.repository.JobRepository;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import com.example.clipbot_backend.util.AssetKind;
import com.example.clipbot_backend.util.JobStatus;
import com.example.clipbot_backend.util.JobType;
import com.example.clipbot_backend.util.MediaStatus;
import com.example.clipbot_backend.util.SpeakerMode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class UploadService {
    private final StorageService storageService;
    private final AccountRepository accountRepo;
    private final MediaRepository mediarepo;
    private final AssetRepository assetRepo;
    private final JobRepository jobRepo;
    private final JobService jobService;

    public UploadService(StorageService storageService, AccountRepository accountRepo, MediaRepository mediarepo, AssetRepository assetRepo, JobRepository jobRepo, JobService jobService) {
        this.storageService = storageService;
        this.accountRepo = accountRepo;
        this.mediarepo = mediarepo;
        this.assetRepo = assetRepo;
        this.jobRepo = jobRepo;
        this.jobService = jobService;
    }

    public UploadInitResponse init (UploadInitRequest req){
        var sub = req.ownerExternalSubject();
        if(sub == null || sub.isBlank()){
            throw new IllegalArgumentException("ownerExternalSubject is required");
        }
        var keyPrefix = (req.keyPrefix() == null || req.keyPrefix().isBlank()) ?
                ("users/" + sub + "/raw/") : sanitizePrefix(req.keyPrefix());
        String fname = (req.originalFilename() == null || req.originalFilename().isBlank()) ?
                ("upload-" + UUID.randomUUID() + ".mp4") : req.originalFilename().replaceAll("\\s+", "_");
        var objectKey = keyPrefix.replaceAll("^/+", "") + fname;
        return new UploadInitResponse(objectKey);
    }

    @Transactional
    public UploadCompleteResponse uploadLocal(String ownerExternalSubject,
                                              String objectKey,
                                              MultipartFile file,
                                              String sourceLabel,
                                              Boolean podcastOrInterview) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FILE_EMPTY");
        }
        if (ownerExternalSubject == null || ownerExternalSubject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OWNER_REQUIRED");
        }
        objectKey = (objectKey == null || objectKey.isBlank())
                ? "users/" + ownerExternalSubject + "/raw/" + UUID.randomUUID() + ".mp4"
                : objectKey.replace('\\','/').replaceAll("^/+","");
        if (objectKey.contains(".."))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "INVALID_OBJECT_KEY");
        // 1) Ensure account
        var owner = accountRepo.findByExternalSubject(ownerExternalSubject).orElseGet(() -> accountRepo.save(new Account(ownerExternalSubject, ownerExternalSubject)));

        var media = new Media(owner, objectKey);
        media.setSource(sourceLabel == null ? "upload" : sourceLabel.trim().toLowerCase());
        SpeakerMode speakerMode = Boolean.TRUE.equals(podcastOrInterview) ? SpeakerMode.MULTI : SpeakerMode.SINGLE;
        if (SpeakerMode.MULTI.equals(speakerMode)) {
            org.slf4j.LoggerFactory.getLogger(UploadService.class)
                    .info("INGEST upload podcastOrInterview=true speakerMode=MULTI owner={} objectKey={}", ownerExternalSubject, objectKey);
        }
        media.setSpeakerMode(speakerMode);
        media.setStatus(MediaStatus.REGISTERED);
        mediarepo.saveAndFlush(media);
        Path tmp = null;
        try {
            // 2) Write file to RAW
            tmp = Files.createTempFile("upload-", ".bin");
            file.transferTo(tmp.toFile());
            long sizeBytes = Files.size(tmp);

            storageService.uploadToRaw(tmp, objectKey);

            // 3) Register asset
            Asset asset = new Asset(owner, AssetKind.MEDIA_RAW, objectKey, sizeBytes);
            asset.setRelatedMedia(media);
            assetRepo.save(asset);

            media.setStatus(MediaStatus.UPLOADED);
            mediarepo.save(media);

            jobService.enqueue(media.getId(), JobType.TRANSCRIBE, Map.of());

            return new UploadCompleteResponse(media.getId(), asset.getId(), objectKey, sizeBytes);

        } catch (Exception e) {
            // Failure: markeer media FAILED als hij bestaat
            try {
                if (media.getId() != null) {
                    media.setStatus(MediaStatus.FAILED);
                    mediarepo.save(media);
                }
            } catch (Exception ignore) {
            }
            throw e;
        } finally {
            if (tmp != null) try {
                Files.deleteIfExists(tmp);
            } catch (Exception ignore) {
            }
        }
    }
    private String sanitizePrefix(String p) {
        var s = p.replace('\\', '/');
        if (!s.endsWith("/")) s = s + "/";
        return s;
    }
}
