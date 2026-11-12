package com.agent.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerId;

    @Column(length = 36, unique = true)
    private String publicId;

    @Column(length = 80, unique = true)
    private String creationRequestId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 60)
    private String type;

    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'manual'")
    private String creationMode = "manual";

    @Column(length = 2000, columnDefinition = "varchar(2000) default ''")
    private String description = "";

    @Column(nullable = false, length = 16)
    private String color;

    @Column(nullable = false, length = 40)
    private String status = "草稿";

    @Column(nullable = false)
    private int terminals = 0;

    @Column(nullable = false)
    private int fileCount = 0;

    @Column(nullable = false, length = 30)
    private String topologyVersion = "V0";

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Project() {
    }

    public Project(Long ownerId, String name, String type, String color) {
        this(ownerId, name, type, color, "manual", "");
    }

    public Project(Long ownerId, String name, String type, String color, String creationMode, String description) {
        this.ownerId = ownerId;
        this.publicId = UUID.randomUUID().toString();
        this.name = name;
        this.type = type;
        this.color = color;
        this.creationMode = creationMode;
        this.description = description == null ? "" : description;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void update(String name, String type, String color, String status, Integer terminals, String creationMode, String description) {
        if (name != null && !name.isBlank()) this.name = name.trim();
        if (type != null && !type.isBlank()) this.type = type.trim();
        if (color != null && !color.isBlank()) this.color = color.trim();
        if (status != null && !status.isBlank()) this.status = status.trim();
        if (terminals != null && terminals >= 0) this.terminals = terminals;
        if (creationMode != null && !creationMode.isBlank()) this.creationMode = creationMode.trim();
        if (description != null) this.description = description.trim();
        this.updatedAt = Instant.now();
    }

    public void updateFileCount(int count) { this.fileCount = count; this.updatedAt = Instant.now(); }
    public void updateTopologyVersion(String version) { this.topologyVersion = version; this.updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public void setCreationRequestId(String value) { this.creationRequestId = value; }
    public String getCreationRequestId() { return creationRequestId; }
    public void ensurePublicId() { if (publicId == null || publicId.isBlank()) publicId = UUID.randomUUID().toString(); }
    @PrePersist
    void beforePersist() { ensurePublicId(); }
    public Long getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getCreationMode() { return creationMode; }
    public String getDescription() { return description; }
    public String getColor() { return color; }
    public String getStatus() { return status; }
    public int getTerminals() { return terminals; }
    public int getFileCount() { return fileCount; }
    public String getTopologyVersion() { return topologyVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
