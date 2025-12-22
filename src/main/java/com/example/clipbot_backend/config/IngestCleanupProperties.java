package com.example.clipbot_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls how ingest failures are cleaned up.
 */
@ConfigurationProperties(prefix = "ingest.cleanup")
public class IngestCleanupProperties {
    private boolean deleteProjectOnDownloadFail = true;
    private boolean deleteFiles = true;

    public boolean isDeleteProjectOnDownloadFail() {
        return deleteProjectOnDownloadFail;
    }

    public void setDeleteProjectOnDownloadFail(boolean deleteProjectOnDownloadFail) {
        this.deleteProjectOnDownloadFail = deleteProjectOnDownloadFail;
    }

    public boolean isDeleteFiles() {
        return deleteFiles;
    }

    public void setDeleteFiles(boolean deleteFiles) {
        this.deleteFiles = deleteFiles;
    }
}
