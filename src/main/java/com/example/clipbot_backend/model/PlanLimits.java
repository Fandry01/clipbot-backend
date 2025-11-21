package com.example.clipbot_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Lookup table containing per-plan entitlement limits.
 */
@Entity
@Table(name = "plan_limits")
public class PlanLimits {
    @Id
    @Column(name = "plan")
    private String plan;

    @Column(name = "max_renders_day", nullable = false)
    private int maxRendersDay;

    @Column(name = "max_renders_month", nullable = false)
    private int maxRendersMonth;

    @Column(name = "allow_1080p", nullable = false)
    private boolean allow1080p;

    @Column(name = "allow_4k", nullable = false)
    private boolean allow4k;

    @Column(name = "watermark", nullable = false)
    private boolean watermark;

    public String getPlan() {
        return plan;
    }

    public int getMaxRendersDay() {
        return maxRendersDay;
    }

    public int getMaxRendersMonth() {
        return maxRendersMonth;
    }

    public boolean isAllow1080p() {
        return allow1080p;
    }

    public boolean isAllow4k() {
        return allow4k;
    }

    public boolean isWatermark() {
        return watermark;
    }
}
