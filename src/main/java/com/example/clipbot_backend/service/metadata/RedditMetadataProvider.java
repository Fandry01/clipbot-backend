package com.example.clipbot_backend.service.metadata;

import com.example.clipbot_backend.util.MediaPlatform;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
@Order(20)
class RedditMetadataProvider implements MetadataProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    RedditMetadataProvider(@Qualifier("metadataWebClient") WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MediaPlatform platform) {
        return platform == MediaPlatform.REDDIT;
    }

    @Override
    public Optional<MetadataResult> resolve(URI uri, MediaPlatform platform) {
        String endpoint = uri.toString() + (uri.toString().endsWith(".json") ? "" : ".json");
        try {
            String payload = webClient.get()
                    .uri(endpoint)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (payload == null || payload.isBlank()) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(payload);
            if (!root.isArray() || root.isEmpty()) {
                return Optional.empty();
            }
            JsonNode data = root.get(0).path("data").path("children");
            if (!data.isArray() || data.isEmpty()) {
                return Optional.empty();
            }
            JsonNode entry = data.get(0).path("data");
            String title = textOrNull(entry, "title");
            String author = textOrNull(entry, "author");
            String thumbnail = textOrNull(entry, "thumbnail");
            Long duration = null;
            JsonNode redditVideo = entry.path("media").path("reddit_video");
            if (redditVideo.hasNonNull("duration")) {
                duration = redditVideo.get("duration").asLong();
            }
            return Optional.of(new MetadataResult(platform, uri.toString(), title, author, duration, thumbnail));
        } catch (WebClientResponseException ex) {
            HttpStatusCode status = ex.getStatusCode();
            if (status.is4xxClientError()) {
                if (status.value() == 403 || status.value() == 401 || status.value() == 404) {
                    return Optional.of(MetadataResult.empty(platform, uri.toString()));
                }
                return Optional.empty();
            }
            throw new MetadataAccessException("reddit metadata lookup failed", ex);
        } catch (WebClientRequestException | java.io.IOException ex) {
            throw new MetadataAccessException("reddit metadata lookup failed", ex);
        }
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field)) {
            return null;
        }
        var value = node.path(field).asText();
        return value != null && !value.isBlank() ? value : null;
    }
}
