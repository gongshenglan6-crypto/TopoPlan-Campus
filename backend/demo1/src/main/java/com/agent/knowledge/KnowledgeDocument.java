package com.agent.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;
    @Column(nullable = false, length = 60)
    private String category;
    @Column(nullable = false, length = 120)
    private String uploader;
    @Column(nullable = false, length = 20)
    private String format;
    @Column(nullable = false)
    private long size;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false)
    private int chunkCount;
    @Column(length = 500)
    private String errorMessage;
    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String extractedText;
    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] originalContent;
    @Column(nullable = false)
    private Instant uploadedAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected KnowledgeDocument() {
    }

    public KnowledgeDocument(String name, String category, String uploader, String format, long size, byte[] originalContent) {
        this.name = name;
        this.category = category;
        this.uploader = uploader;
        this.format = format;
        this.size = size;
        this.originalContent = originalContent;
        this.extractedText = "";
        this.status = "pending";
        this.chunkCount = 0;
        this.uploadedAt = Instant.now();
        this.updatedAt = this.uploadedAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getUploader() { return uploader; }
    public String getFormat() { return format; }
    public long getSize() { return size; }
    public String getStatus() { return status; }
    public int getChunkCount() { return chunkCount; }
    public String getErrorMessage() { return errorMessage; }
    public String getExtractedText() { return extractedText; }
    public byte[] getOriginalContent() { return originalContent; }
    public Instant getUploadedAt() { return uploadedAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateProcessing(String status, String extractedText, int chunkCount, String errorMessage) {
        this.status = status;
        this.extractedText = extractedText == null ? "" : extractedText;
        this.chunkCount = chunkCount;
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }
}
