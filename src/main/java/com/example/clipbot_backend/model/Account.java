package com.example.clipbot_backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

import java.util.UUID;

import com.example.clipbot_backend.model.PlanTier;

@Entity
public class
Account {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "external_subject", nullable = false, unique = true)
    private String externalSubject;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "email", length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_tier", nullable = false, columnDefinition = "text")
    private PlanTier planTier = PlanTier.TRIAL;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "is_admin", nullable = false)
    private boolean admin = false;

    public boolean isAdmin() { return admin; }
    public Account() {
    }

    public Account(String externalSubject, String displayName) {
        this.externalSubject = externalSubject;
        this.displayName = displayName;
    }

    public UUID getId() {
        return id;
    }

    public String getExternalSubject() {
        return externalSubject;
    }

    public void setExternalSubject(String externalSubject) {
        this.externalSubject = externalSubject;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public PlanTier getPlanTier() {
        return planTier;
    }

    public void setPlanTier(PlanTier planTier) {
        this.planTier = planTier;
    }

    public Instant getTrialEndsAt() {
        return trialEndsAt;
    }

    public void setTrialEndsAt(Instant trialEndsAt) {
        this.trialEndsAt = trialEndsAt;
    }
}
