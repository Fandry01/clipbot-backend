package com.example.clipbot_backend.service.Interfaces;

import com.example.clipbot_backend.model.Media;
import com.example.clipbot_backend.util.MediaStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface MediaService {
    UUID createMedia(UUID ownerId, String objectKey, String source);
    Page<Media> listByOwner(UUID ownerId, Pageable p);
    Media get(UUID mediaId);
    void setStatus(UUID mediaId, MediaStatus status);
    void setDuration(UUID mediaId, long durationMs);
}
