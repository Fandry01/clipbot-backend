package com.example.clipbot_backend.config;

import com.example.clipbot_backend.util.UploadMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "asr.api")
public class ApiAsrProperties {
    private String baseUrl;
    private String apiKey;
    private UploadMode uploadMode = UploadMode.DIRECT;

    private String transcribePath = "/v1/transcriptions";
    private String statusPath = "/v1/transcriptions/{id}";

    private String uploadHeaderName;
    private String uploadHeaderValue;

    private String model = "medium";
    private String language = "auto";

    private long timeoutSeconds = 600;
    private long pollIntervalMs = 1500;
    private int  pollMaxAttempts = 400;

    private String jsonTextPath = "text";
    private String jsonLangPath = "language";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public UploadMode getUploadMode() {
        return uploadMode;
    }

    public void setUploadMode(UploadMode uploadMode) {
        this.uploadMode = uploadMode;
    }

    public String getTranscribePath() {
        return transcribePath;
    }

    public void setTranscribePath(String transcribePath) {
        this.transcribePath = transcribePath;
    }

    public String getStatusPath() {
        return statusPath;
    }

    public void setStatusPath(String statusPath) {
        this.statusPath = statusPath;
    }

    public String getUploadHeaderName() {
        return uploadHeaderName;
    }

    public void setUploadHeaderName(String uploadHeaderName) {
        this.uploadHeaderName = uploadHeaderName;
    }

    public String getUploadHeaderValue() {
        return uploadHeaderValue;
    }

    public void setUploadHeaderValue(String uploadHeaderValue) {
        this.uploadHeaderValue = uploadHeaderValue;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getPollMaxAttempts() {
        return pollMaxAttempts;
    }

    public void setPollMaxAttempts(int pollMaxAttempts) {
        this.pollMaxAttempts = pollMaxAttempts;
    }

    public String getJsonTextPath() {
        return jsonTextPath;
    }

    public void setJsonTextPath(String jsonTextPath) {
        this.jsonTextPath = jsonTextPath;
    }

    public String getJsonLangPath() {
        return jsonLangPath;
    }

    public void setJsonLangPath(String jsonLangPath) {
        this.jsonLangPath = jsonLangPath;
    }
}
