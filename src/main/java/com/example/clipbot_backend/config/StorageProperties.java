package com.example.clipbot_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage.local")
public class StorageProperties {
    private String baseDir = "./data";
    private String rawPrefix = "raw";
    private String outPrefix = "out";

    public String getBaseDir() { return baseDir; }
    public void setBaseDir(String baseDir) { this.baseDir = baseDir; }

    public String getRawPrefix() { return rawPrefix; }
    public void setRawPrefix(String rawPrefix) { this.rawPrefix = rawPrefix; }

    public String getOutPrefix() { return outPrefix; }
    public void setOutPrefix(String outPrefix) { this.outPrefix = outPrefix; }
}
