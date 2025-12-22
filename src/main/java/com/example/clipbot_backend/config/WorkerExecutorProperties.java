package com.example.clipbot_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configures worker polling and concurrency limits for background job execution.
 */
@ConfigurationProperties(prefix = "worker")
public class WorkerExecutorProperties {

    private int pollBatchSize = 5;
    private int executorThreads = 6;
    private int executorQueueCapacity = 50;

    private Concurrency clip = new Concurrency(2);
    private Concurrency transcribe = new Concurrency(1);
    private Concurrency detect = new Concurrency(1);

    public int getPollBatchSize() {
        return pollBatchSize;
    }

    public void setPollBatchSize(int pollBatchSize) {
        this.pollBatchSize = pollBatchSize;
    }

    public int getExecutorThreads() {
        return executorThreads;
    }

    public void setExecutorThreads(int executorThreads) {
        this.executorThreads = executorThreads;
    }

    public int getExecutorQueueCapacity() {
        return executorQueueCapacity;
    }

    public void setExecutorQueueCapacity(int executorQueueCapacity) {
        this.executorQueueCapacity = executorQueueCapacity;
    }

    public Concurrency getClip() {
        return clip;
    }

    public void setClip(Concurrency clip) {
        this.clip = clip;
    }

    public Concurrency getTranscribe() {
        return transcribe;
    }

    public void setTranscribe(Concurrency transcribe) {
        this.transcribe = transcribe;
    }

    public Concurrency getDetect() {
        return detect;
    }

    public void setDetect(Concurrency detect) {
        this.detect = detect;
    }

    /**
     * Returns the maximum configured concurrency across all job types to help size executors.
     *
     * @return maximum configured concurrency.
     */
    public int maxConfiguredConcurrency() {
        return Math.max(clip.getMaxConcurrency(), Math.max(transcribe.getMaxConcurrency(), detect.getMaxConcurrency()));
    }

    public static class Concurrency {
        private int maxConcurrency = 1;

        public Concurrency() {
        }

        public Concurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
        }

        public int getMaxConcurrency() {
            return maxConcurrency;
        }

        public void setMaxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
        }
    }
}
