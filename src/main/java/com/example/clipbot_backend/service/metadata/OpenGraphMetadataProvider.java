package com.example.clipbot_backend.service.metadata;

import com.example.clipbot_backend.util.MediaPlatform;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.HtmlUtils;

@Component
@Order(200)
class OpenGraphMetadataProvider implements MetadataProvider {

    private static final Pattern META_PATTERN = Pattern.compile("(?i)<meta\\s+[^>]*>");
    private static final Pattern ATTR_PATTERN = Pattern.compile("(?i)([a-z0-9:-]+)\\s*=\\s*['\"]([^'\"]*)['\"]");
    private static final Pattern TITLE_PATTERN = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");

    private final WebClient webClient;

    OpenGraphMetadataProvider(@Qualifier("metadataWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public boolean supports(MediaPlatform platform) {
        return true;
    }

    @Override
    public Optional<MetadataResult> resolve(URI uri, MediaPlatform platform) {
        try {
            String body = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }
            String title = firstNonBlank(
                    findMeta(body, "og:title"),
                    findMeta(body, "twitter:title"),
                    extractTitle(body)
            );
            String author = firstNonBlank(findMeta(body, "og:site_name"), findMeta(body, "twitter:creator"));
            String thumbnail = firstNonBlank(findMeta(body, "og:image"), findMeta(body, "twitter:image"));
            Long duration = parseDuration(firstNonBlank(findMeta(body, "og:video:duration"), findMeta(body, "video:duration")));

            MetadataResult partial = new MetadataResult(platform, uri.toString(), title, author, duration, thumbnail);
            if (!partial.hasAnyData()) {
                return Optional.empty();
            }
            return Optional.of(partial);
        } catch (WebClientResponseException ex) {
            HttpStatusCode status = ex.getStatusCode();
            if (status.is4xxClientError()) {
                if (status.value() == 403 || status.value() == 401) {
                    return Optional.of(MetadataResult.empty(platform, uri.toString()));
                }
                return Optional.empty();
            }
            throw new MetadataAccessException("open graph lookup failed", ex);
        } catch (WebClientRequestException ex) {
            throw new MetadataAccessException("open graph lookup failed", ex);
        }
    }

    private String findMeta(String body, String key) {
        Matcher matcher = META_PATTERN.matcher(body);
        while (matcher.find()) {
            String tag = matcher.group();
            Matcher attrMatcher = ATTR_PATTERN.matcher(tag);
            String property = null;
            String content = null;
            while (attrMatcher.find()) {
                String name = attrMatcher.group(1).toLowerCase(Locale.ROOT);
                String value = attrMatcher.group(2);
                if (("property".equals(name) || "name".equals(name)) && value != null && !value.isBlank()) {
                    property = value;
                } else if ("content".equals(name) && value != null && !value.isBlank()) {
                    content = value;
                }
            }
            if (property != null && property.equalsIgnoreCase(key) && content != null) {
                return HtmlUtils.htmlUnescape(content);
            }
        }
        return null;
    }

    private String extractTitle(String body) {
        Matcher matcher = TITLE_PATTERN.matcher(body);
        if (matcher.find()) {
            return HtmlUtils.htmlUnescape(matcher.group(1).trim());
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Long parseDuration(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
