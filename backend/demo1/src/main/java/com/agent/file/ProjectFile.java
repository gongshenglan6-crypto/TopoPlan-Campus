package com.agent.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "project_files")
public class ProjectFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long projectId;
    @Column(nullable = false)
    private Long ownerId;
    @Column(nullable = false, length = 255)
    private String name;
    @Column(nullable = false, length = 20)
    private String format;
    @Column(nullable = false)
    private long size;
    @Column(nullable = false, length = 30)
    private String status;
    @Column(nullable = false, length = 255)
    private String storageName;
    @Lob
    @Column(columnDefinition = "MEDIUMTEXT")
    private String extractedText;
    @Column(length = 500)
    private String errorMessage;
    @Column(nullable = false)
    private Instant createdAt;

    protected ProjectFile() {
    }

    public ProjectFile(Long projectId, Long ownerId, String name, String format, long size, String status, String storageName) {
        this(projectId, ownerId, name, format, size, status, storageName, "", null);
    }

    public ProjectFile(Long projectId, Long ownerId, String name, String format, long size, String status, String storageName,
                       String extractedText, String errorMessage) {
        this.projectId = projectId;
        this.ownerId = ownerId;
        this.name = name;
        this.format = format;
        this.size = size;
        this.status = status;
        this.storageName = storageName;
        this.extractedText = extractedText == null ? "" : extractedText;
        this.errorMessage = errorMessage;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public Long getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getFormat() { return format; }
    public long getSize() { return size; }
    public String getStatus() { return status; }
    public String getExtractedText() { return extractedText == null ? "" : extractedText; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean hasReadableText() { return !getExtractedText().isBlank() && "已读取".equals(status); }
}
