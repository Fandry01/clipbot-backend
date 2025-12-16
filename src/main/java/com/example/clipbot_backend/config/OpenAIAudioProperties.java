package com.example.clipbot_backend.config;


import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "asr.openai")
public class OpenAIAudioProperties {

    private String baseUrl = "https://api.openai.com";
    private String apiKey;
    private String model = "gpt-4o-transcribe-diarize";
    private String language = "auto";
    private long timeoutSeconds = 2700;
    private Boolean diarize;


    public OpenAIAudioProperties() {
    }

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

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public boolean isDiarize() {
        if (diarize != null) {
            return diarize;
        }
        return model != null && model.toLowerCase().contains("diarize");
    }

    public void setDiarize(Boolean diarize) {
        this.diarize = diarize;
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
}
