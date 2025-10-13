package com.example.clipbot_backend.service.metadata;

import com.example.clipbot_backend.util.MediaPlatform;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
@Order(10)
class NoembedMetadataProvider implements MetadataProvider {

    private static final EnumSet<MediaPlatform> SUPPORTED = EnumSet.of(MediaPlatform.YOUTUBE, MediaPlatform.VIMEO);
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    NoembedMetadataProvider(@Qualifier("metadataWebClient") WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MediaPlatform platform) {
        return SUPPORTED.contains(platform);
    }

    @Override
    public Optional<MetadataResult> resolve(URI uri, MediaPlatform platform) {
        String endpoint = "https://noembed.com/embed?url=" + URLEncoder.encode(uri.toString(), StandardCharsets.UTF_8);
        try {
            String payload = webClient.get()
                    .uri(endpoint)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (payload == null || payload.isBlank()) {
                return Optional.empty();
            }
            JsonNode node = objectMapper.readTree(payload);
            String title = textOrNull(node, "title");
            String author = textOrNull(node, "author_name");
            String thumbnail = textOrNull(node, "thumbnail_url");
            Long duration = node.hasNonNull("duration") ? node.get("duration").asLong() : null;
            return Optional.of(new MetadataResult(platform, uri.toString(), title, author, duration, thumbnail));
        } catch (WebClientResponseException ex) {
            HttpStatusCode status = ex.getStatusCode();
            if (status.is4xxClientError()) {
                if (status.value() == 403 || status.value() == 401 || status.value() == 404) {
                    return Optional.of(MetadataResult.empty(platform, uri.toString()));
                }
                return Optional.empty();
            }
            throw new MetadataAccessException("noembed lookup failed", ex);
        } catch (WebClientRequestException | java.io.IOException ex) {
            throw new MetadataAccessException("noembed lookup failed", ex);
        }
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        var value = node.get(field).asText();
        return value != null && !value.isBlank() ? value : null;
    }
}
