package com.example.clipbot_backend.dto;

import com.example.clipbot_backend.model.Clip;
import com.example.clipbot_backend.repository.AssetRepository;
import com.example.clipbot_backend.util.AssetKind;
import com.example.clipbot_backend.util.AssetUrlBuilder;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClipResponse(
        UUID id,
        UUID mediaId,
        long startMs,
        long endMs,
        String title,
        Map<String,Object> meta,
        String status,
        String captionSrtKey,
        String captionVttKey,
        String thumbUrl,
        String mp4Url,
        String subtitlesMode,
        Instant createdAt
) {
    public static ClipResponse from(Clip c, AssetRepository assetRepo) {
        var thumb = assetRepo.findTopByRelatedClipAndKindOrderByCreatedAtDesc(c, AssetKind.THUMBNAIL).orElse(null);
        var mp4   = assetRepo.findTopByRelatedClipAndKindOrderByCreatedAtDesc(c, AssetKind.MP4).orElse(null);
        return new ClipResponse(
                c.getId(),
                c.getMedia().getId(),
                c.getStartMs(),
                c.getEndMs(),
                c.getTitle(),
                c.getMeta(),
                c.getStatus().name(),
                c.getCaptionSrtKey(),
                c.getCaptionVttKey(),
                thumb != null ? AssetUrlBuilder.out(thumb.getObjectKey()) : null,
                mp4   != null ? AssetUrlBuilder.out(mp4.getObjectKey())   : null,
                "burned",
                c.getCreatedAt()
        );
    }
}
