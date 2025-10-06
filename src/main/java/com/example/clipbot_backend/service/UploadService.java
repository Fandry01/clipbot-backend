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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

@Service
public class UploadService {
    private final StorageService storageService;
    private final AccountRepository accountRepo;
    private final MediaRepository mediarepo;
    private final AssetRepository assetRepo;
    private final JobRepository jobRepo;

    public UploadService(StorageService storageService, AccountRepository accountRepo, MediaRepository mediarepo, AssetRepository assetRepo, JobRepository jobRepo) {
        this.storageService = storageService;
        this.accountRepo = accountRepo;
        this.mediarepo = mediarepo;
        this.assetRepo = assetRepo;
        this.jobRepo = jobRepo;
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
                                              String sourceLabel) throws Exception{
        if (file == null || file.isEmpty()){
            throw new IllegalArgumentException("file is empty");
        }
        if (ownerExternalSubject == null || ownerExternalSubject.isBlank()) {
            throw new IllegalArgumentException("ownerExternalSubject is required");
        }
        if (objectKey == null || objectKey.isBlank()) {
            // fallback: users/{sub}/raw/{uuid}.mp4
            objectKey = "users/" + ownerExternalSubject + "/raw/" + UUID.randomUUID() + ".mp4";
        }
        // 1) Ensure account
        var owner = accountRepo.findByExternalSubject(ownerExternalSubject).orElseGet(() -> accountRepo.save(new Account(ownerExternalSubject, ownerExternalSubject)));

        // 2) Write file to RAW
        Path tmp = Files.createTempFile("upload-", ".bin");
        file.transferTo(tmp.toFile());
        storageService.uploadToRaw(tmp, objectKey);
        long sizeBytes = Files.size(tmp);
        Files.deleteIfExists(tmp);

        // 3) Register asset
        var asset = new Asset(owner, AssetKind.MEDIA_RAW, objectKey, sizeBytes);
        assetRepo.save(asset);

        // 4) Create Media

        var media = new Media(owner, objectKey);
        media.setSource(sourceLabel != null ? sourceLabel : "upload");
        media.setStatus(MediaStatus.UPLOADED);
        mediarepo.save(media);

        // 5) Enqueue TRANSCRIBE job
        var job = new Job(JobType.TRANSCRIBE);
        job.setMedia(media);
        job.setStatus(JobStatus.QUEUED);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        jobRepo.save(job);

        return new UploadCompleteResponse(media.getId(), asset.getId(), objectKey, sizeBytes);
    }

    private String sanitizePrefix(String p) {
        var s = p.replace('\\', '/');
        if (!s.endsWith("/")) s = s + "/";
        return s;
    }
}
