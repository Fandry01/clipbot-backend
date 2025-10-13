package com.example.clipbot_backend.service;

import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.repository.AccountRepository;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.util.MediaPlatform;
import com.example.clipbot_backend.util.MediaStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MediaService implements com.example.clipbot_backend.service.Interfaces.MediaService {
    private final MediaRepository mediaRepo;
    private final AccountRepository accountRepo;

    public MediaService(MediaRepository mediaRepo, AccountRepository accountRepo) {
        this.mediaRepo = mediaRepo;
        this.accountRepo = accountRepo;
    }

    @Override
    @Transactional
    public UUID createMedia(UUID ownerId, String objectKey, String source) {
        var owner = accountRepo.findById(ownerId).orElseThrow();
        var m = new Media(owner, objectKey);
        m.setSource(source);
        m.setStatus(MediaStatus.UPLOADED);
        mediaRepo.save(m);
        return m.getId();
    }
    @Override
    public Page<Media> listByOwner(UUID ownerId, Pageable p) {
        var owner = accountRepo.findById(ownerId).orElseThrow();
        return mediaRepo.findByOwnerOrderByCreatedAtDesc(owner, p);
    }

    @Override
    public Media get(UUID mediaId) {
        return mediaRepo.findById(mediaId).orElseThrow();
    }

    @Override
    @Transactional
    public void setStatus(UUID mediaId, MediaStatus status) {
        var media = mediaRepo.findById(mediaId).orElseThrow();
        media.setStatus(status);
        mediaRepo.save(media);
    }

    @Override
    @Transactional
    public  void setDuration(UUID mediaId, long durationMs){
        var media = mediaRepo.findById(mediaId).orElseThrow();
        media.setDurationMs(durationMs);
        mediaRepo.save(media);
    }

    @Override
    @Transactional
    public UUID createMediaFromUrl(UUID ownerId, String externalUrl, MediaPlatform platform, String source, Long durationMs) {
        var owner = accountRepo.findById(ownerId).orElseThrow();
        var media = new Media();
        media.setOwner(owner);
        media.setExternalUrl(externalUrl);
        media.setPlatform(platform != null ? platform.id() : null);
        media.setSource((source == null || source.isBlank()) ? "url" : source);
        media.setStatus(MediaStatus.REGISTERED);
        media.setDurationMs(durationMs);
        mediaRepo.save(media);
        return media.getId();
    }

}
