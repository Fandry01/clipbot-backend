package com.example.clipbot_backend.service;

import com.example.clipbot_backend.model.Media;

import com.example.clipbot_backend.repository.MediaRepository;
import com.example.clipbot_backend.util.MediaPlatform;
import com.example.clipbot_backend.util.MediaStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;


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
        var m = new Media(owner, objectKey);
        m.setSource(source);
        m.setStatus(MediaStatus.UPLOADED);
        mediaRepo.save(m);
        return m.getId();
    }


    public Page<Media> listByOwner(UUID ownerId, Pageable p) {
        var owner = accountService.getByIdOrThrow(ownerId);
        return mediaRepo.findByOwnerOrderByCreatedAtDesc(owner, p);
    }


    public Media get(UUID mediaId) {
        return mediaRepo.findById(mediaId).orElseThrow();
    }


    @Transactional
    public void setStatus(UUID mediaId, MediaStatus status) {
        var media = mediaRepo.findById(mediaId).orElseThrow();
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
            String objectKeyOverride // ← gebruik dit als caller er eentje meegeeft
    ) {
        var owner = accountService.getByIdOrThrow(ownerId);

        var media = new Media();
        media.setOwner(owner);
        media.setExternalUrl(externalUrl);
        media.setPlatform(platform != null ? platform.id() : null);
        media.setSource((source == null || source.isBlank()) ? "url" : source);

        // Status die in je CHECK-constraint toegestaan is
        media.setStatus(MediaStatus.UPLOADED);

        media.setDurationMs(durationMs);

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

    private String buildExternalObjectKey(String url, MediaPlatform platform) {
        // map platform → slug
        String slug = switch (platform) {
            case YOUTUBE -> "yt";
            case VIMEO -> "vimeo";
            case TWITCH -> "twitch";
            case FACEBOOK -> "fb";
            case X -> "x";
            case RUMBLE -> "rumble";
            case LINKEDIN -> "li";
            default -> "url";
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

    // Zeer simpele extracties; je kunt ze later uitbreiden
    private String extractPlatformId(String rawUrl, MediaPlatform platform) {
        try {
            var uri = new java.net.URI(rawUrl);
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
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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

