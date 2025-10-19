package com.example.clipbot_backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

import java.util.UUID;

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

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    private Instant createdAt;

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
}
