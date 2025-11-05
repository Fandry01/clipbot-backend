package com.example.clipbot_backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "template")
public class Template {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_template_owner"))
    private Account owner;

    @Column(nullable = false, length = 200)
    private String name;

    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "json_config", nullable = false, columnDefinition = "jsonb")
    private String jsonConfig; // sla volledige config als JSON string

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist void prePersist(){ createdAt = updatedAt = Instant.now(); }
    @PreUpdate  void preUpdate(){ updatedAt = Instant.now(); }

    @Version
    @Column(name = "version", nullable = false)
    private long version;
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Account getOwner() {
        return owner;
    }

    public void setOwner(Account owner) {
        this.owner = owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJsonConfig() {
        return jsonConfig;
    }

    public void setJsonConfig(String jsonConfig) {
        this.jsonConfig = jsonConfig;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
