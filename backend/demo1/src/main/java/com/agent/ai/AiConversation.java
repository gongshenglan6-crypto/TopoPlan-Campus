package com.agent.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.Instant;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class AiConversation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long userId;
    @Column(name = "public_id", unique = true, nullable = false, length = 36)
    private String publicId;
    @Column(name = "public_token_hash", unique = true, length = 128)
    private String publicTokenHash;
    private Long projectId;
    @Column(name = "conversation_type", length = 20)
    private String conversationType;
    @Column(nullable = false, length = 160) private String title;
    @Column(name = "title_status", length = 20) private String titleStatus;
    private Long modelId;
    @Column(nullable = false) private boolean pinned;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Column(nullable = false) private Instant lastMessageAt;

    protected AiConversation() { }
    public AiConversation(Long userId, Long projectId, String title, Long modelId) {
        this.userId = userId;
        this.publicId = UUID.randomUUID().toString();
        this.projectId = projectId;
        this.conversationType = projectId == null ? "GENERAL_CHAT" : "PROJECT_CHAT";
        this.title = title;
        this.titleStatus = "DEFAULT";
        this.publicTokenHash = createOpaqueToken();
        this.modelId = modelId;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        this.lastMessageAt = createdAt;
    }
    public void touch(Long modelId) { if (modelId != null) this.modelId = modelId; updatedAt = Instant.now(); lastMessageAt = updatedAt; }
    public void update(String title, Boolean pinned) {
        if (title != null && !title.isBlank()) { this.title = title.trim(); this.titleStatus = "MANUAL"; }
        if (pinned != null) this.pinned = pinned;
        updatedAt = Instant.now();
    }
    public void autoTitle(String title) {
        if (title == null || title.isBlank() || "MANUAL".equalsIgnoreCase(titleStatus)) return;
        this.title = title.trim();
        this.titleStatus = "GENERATED";
        updatedAt = Instant.now();
    }
    public void ensurePublicToken() { if (publicTokenHash == null || publicTokenHash.isBlank()) publicTokenHash = createOpaqueToken(); }
    public void ensurePublicId() { if (publicId == null || publicId.isBlank()) publicId = UUID.randomUUID().toString(); }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getPublicId() { return publicId; }
    public String getPublicToken() { return publicTokenHash; }
    public String getTitleStatus() { return titleStatus == null || titleStatus.isBlank() ? "DEFAULT" : titleStatus; }
    public Long getProjectId() { return projectId; }
    public boolean ensureConversationType() {
        String expected = projectId == null ? "GENERAL_CHAT" : "PROJECT_CHAT";
        if (expected.equalsIgnoreCase(conversationType)) return false;
        conversationType = expected;
        return true;
    }

    public String getConversationType() {
        if (conversationType == null || conversationType.isBlank()) return projectId == null ? "GENERAL_CHAT" : "PROJECT_CHAT";
        return conversationType;
    }
    public String getTitle() { return title; }
    public Long getModelId() { return modelId; }
    public boolean isPinned() { return pinned; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastMessageAt() { return lastMessageAt; }

    @PrePersist
    @PreUpdate
    private void normalizeConversationType() {
        if (conversationType == null || conversationType.isBlank()) conversationType = projectId == null ? "GENERAL_CHAT" : "PROJECT_CHAT";
    }

    private static String createOpaqueToken() {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(random);
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成会话 Token", exception);
        }
    }
}
