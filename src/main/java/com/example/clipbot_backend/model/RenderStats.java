package com.example.clipbot_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/**
 * Aggregated render statistics used for estimating render durations per profile kind.
 */
@Entity
@Table(name = "render_stats", uniqueConstraints = {
        @UniqueConstraint(name = "ux_render_stats_kind", columnNames = {"kind"})
})
public class RenderStats {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "kind", nullable = false, columnDefinition = "text")
    private String kind;

    @Column(name = "avg_ms", nullable = false)
    private long avgMs;

    @Column(name = "count", nullable = false)
    private long count;

    protected RenderStats() {
    }

    public RenderStats(String kind, long avgMs, long count) {
        this.kind = kind;
        this.avgMs = avgMs;
        this.count = count;
    }

    /**
     * Returns the primary key.
     *
     * @return unique identifier.
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns the render kind (e.g. profile or codec name).
     *
     * @return non-null kind string.
     */
    public String getKind() {
        return kind;
    }

    /**
     * Updates the render kind key.
     *
     * @param kind non-null kind string.
     */
    public void setKind(String kind) {
        this.kind = kind;
    }

    /**
     * Returns the moving-average duration in milliseconds.
     *
     * @return average render duration.
     */
    public long getAvgMs() {
        return avgMs;
    }

    /**
     * Updates the moving-average duration in milliseconds.
     *
     * @param avgMs average render duration.
     */
    public void setAvgMs(long avgMs) {
        this.avgMs = avgMs;
    }

    /**
     * Returns the sample count that contributed to the moving average.
     *
     * @return number of samples.
     */
    public long getCount() {
        return count;
    }

    /**
     * Updates the sample count that contributed to the moving average.
     *
     * @param count number of samples.
     */
    public void setCount(long count) {
        this.count = count;
    }
}
