package com.example.clipbot_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Branding related settings such as watermark path.
 */
@ConfigurationProperties(prefix = "app.brand")
public class BrandProperties {
    private String watermarkPath = "data/brand/watermark.png";

    public String getWatermarkPath() {
        return watermarkPath;
    }

    public void setWatermarkPath(String watermarkPath) {
        this.watermarkPath = watermarkPath;
    }
}
