package com.example.clipbot_backend.service.metadata;

import com.example.clipbot_backend.util.MediaPlatform;

import java.net.URI;
import java.util.Optional;

public interface MetadataProvider {
    boolean supports(MediaPlatform platform);

    Optional<MetadataResult> resolve(URI uri, MediaPlatform platform);
}
