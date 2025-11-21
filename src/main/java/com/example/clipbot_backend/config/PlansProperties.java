package com.example.clipbot_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Plan configuration properties such as trial duration.
 */
@ConfigurationProperties(prefix = "plans")
public class PlansProperties {
    private int trialDays = 14;

    public int getTrialDays() {
        return trialDays;
    }

    public void setTrialDays(int trialDays) {
        this.trialDays = trialDays;
    }
}
