package com.example.clipbot_backend.service;

import com.example.clipbot_backend.model.Media;

import com.example.clipbot_backend.dto.media.CreateFromUrlResponse;
import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.util.MediaPlatform;
import com.example.clipbot_backend.util.MediaStatus;
import com.example.clipbot_backend.util.SpeakerMode;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;


import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@Service
public class MediaService  {
    private final MediaRepository mediaRepo;
    private final AccountService accountService;

    public MediaService(MediaRepository mediaRepo, AccountService accountService) {
        this.mediaRepo = mediaRepo;
        this.accountService = accountService;
    }


    @Transactional
    public UUID createMedia(UUID ownerId, String objectKey, String source) {
        var owner = accountService.getByIdOrThrow(ownerId);
        if (objectKey == null || objectKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OBJECT_KEY_REQUIRED");
        }
        String normalizedSource = normalizeSource(source);
        var m = new Media(owner, normalizeObjectKey(objectKey));
        m.setSource(normalizedSource);
        m.setStatus(MediaStatus.DOWNLOADING);
        mediaRepo.save(m);
        return m.getId();
    }


    public Page<Media> listByOwner(UUID ownerId, Pageable p) {
        var owner = accountService.getByIdOrThrow(ownerId);
        return mediaRepo.findByOwnerOrderByCreatedAtDesc(owner, p);
    }


    public Media get(UUID mediaId) {
        return mediaRepo.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));
    }


    @Transactional
    public void setStatus(UUID mediaId, MediaStatus status) {
        var media = get(mediaId);
        media.setStatus(status);
        mediaRepo.save(media);
    }

    @Transactional
    public void setDuration(UUID mediaId, long durationMs) {
        var media = mediaRepo.findById(mediaId).orElseThrow();
        media.setDurationMs(durationMs);
        mediaRepo.save(media);
    }


    @Transactional
    public UUID createMediaFromUrl(
            UUID ownerId,
            String externalUrl,
            MediaPlatform platform,
            String source,
            Long durationMs,
            String objectKeyOverride, // ← gebruik dit als caller er eentje meegeeft
            SpeakerMode speakerMode
    ) {
        var owner = accountService.getByIdOrThrow(ownerId);

        String normalizedSource = normalizeSource(source);
        if (!"url".equals(normalizedSource)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SOURCE_MUST_BE_URL");
        }
        if (externalUrl == null || externalUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EXTERNAL_URL_REQUIRED");
        }

        var media = new Media();
        media.setOwner(owner);
        media.setExternalUrl(externalUrl);
        media.setPlatform(platform != null ? platform.id() : null);
        media.setSource(normalizedSource);
        media.setStatus(MediaStatus.DOWNLOADING);
        media.setSpeakerMode(speakerMode == null ? SpeakerMode.SINGLE : speakerMode);
        // Status die in je CHECK-constraint toegestaan is
        if (durationMs != null && durationMs > 0) media.setDurationMs(durationMs);

        // Kies objectKey:
        // - Als caller er al een gaf: neem die.
        // - Anders: bouw er één o.b.v. platform + url en voeg ALTIJD bestandsnaam toe.
        String ok = (objectKeyOverride != null && !objectKeyOverride.isBlank())
                ? normalizeObjectKey(objectKeyOverride)
                : buildExternalObjectKey(externalUrl, platform); // geeft pad MET bestandsnaam
        media.setObjectKey(ok);

        mediaRepo.save(media);
        return media.getId();
    }

    public CreateFromUrlResponse createFromUrl(UUID ownerId, String url, String source) {
        UUID mediaId = createMediaFromUrl(ownerId, url, MediaPlatform.OTHER, source, null, null, SpeakerMode.SINGLE);
        return new CreateFromUrlResponse(mediaId);
    }

    private String buildExternalObjectKey(String url, MediaPlatform platform) {
        // map platform → slug
        String slug = (platform == null) ? "url" : switch (platform) {
            case YOUTUBE -> "yt";
            case VIMEO -> "vimeo";
            case TWITCH -> "twitch";
            case FACEBOOK -> "fb";
            case REDDIT -> "reddit";
            case X -> "x";
            case RUMBLE -> "rumble";
            case LINKEDIN -> "li";
            case OTHER -> null;
        };

        String idPart = extractPlatformId(url, platform);
        if (idPart == null || idPart.isBlank()) {
            idPart = shortHash(url); // fallback
        }

        // Conventie: pad Eindigt op een BESTANDSNAAM die we downstream kunnen openen.
        // Audio als standaard is prima voor transcriptie:
        return "ext/" + slug + "/" + idPart + "/source.m4a";
    }

    // kleine helper: backslashes → forward slashes, leading slashes weg
    private String normalizeObjectKey(String key) {
        String k = key.replace('\\', '/').replaceAll("^/+", "");
        // Als caller alleen een map gaf, voeg dan ook een bestandsnaam toe
        if (!k.contains(".")) {
            if (!k.endsWith("/")) k += "/";
            k += "source.m4a";
        }
        return k;
    }
    private String normalizeSource(String source) {
        String s = (source == null ? "" : source.trim().toLowerCase());
        return switch (s) {
            case "upload" -> "upload";
            case "url", "ingest", "" -> "url";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_SOURCE");
        };
    }

    // Zeer simpele extracties; je kunt ze later uitbreiden
    private String extractPlatformId(String rawUrl, MediaPlatform platform) {
        if (platform == null) return null;
        try {
            var uri = new URI(rawUrl);
            var host = (uri.getHost() == null) ? "" : uri.getHost().toLowerCase();
            var path = (uri.getPath() == null) ? "" : uri.getPath();
            var query = (uri.getQuery() == null) ? "" : uri.getQuery();

            switch (platform) {
                case YOUTUBE: {
                    // ?v=ID of youtu.be/ID
                    if (host.contains("youtu.be")) {
                        String[] segs = path.split("/");
                        return segs.length > 1 ? segs[1] : null;
                    }
                    for (String q : query.split("&")) {
                        var kv = q.split("=");
                        if (kv.length == 2 && kv[0].equals("v")) return kv[1];
                    }
                    return null;
                }
                case VIMEO: {
                    // vimeo.com/123456789
                    String[] segs = path.split("/");
                    return segs.length > 1 ? segs[1] : null;
                }
                case TWITCH: {
                    // clips.twitch.tv/<clipId> of /videos/<id>
                    String[] segs = path.split("/");
                    return segs.length > 1 ? segs[segs.length - 1] : null;
                }
                case X: {
                    // x.com/<user>/status/<id>
                    String[] segs = path.split("/");
                    for (int i = 0; i < segs.length - 1; i++) {
                        if ("status".equals(segs[i])) return segs[i + 1];
                    }
                    return null;
                }
                case FACEBOOK: {
                    // fb.watch/<id> of ?v=<id>
                    if (host.contains("fb.watch")) {
                        String[] segs = path.split("/");
                        return segs.length > 1 ? segs[1] : null;
                    }
                    for (String q : query.split("&")) {
                        var kv = q.split("=");
                        if (kv.length == 2 && kv[0].equals("v")) return kv[1];
                    }
                    return null;
                }
                case RUMBLE:
                case LINKEDIN: {
                    // pak laatste path-segment
                    String[] segs = path.split("/");
                    return segs.length > 1 ? segs[segs.length - 1] : null;
                }
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String shortHash(String s) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            // eerste 10 hex chars is genoeg als slug
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) { // 5 bytes → 10 hex chars
                sb.append(String.format("%02x", d[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Long.toHexString(s.hashCode());
        }
    }
}

