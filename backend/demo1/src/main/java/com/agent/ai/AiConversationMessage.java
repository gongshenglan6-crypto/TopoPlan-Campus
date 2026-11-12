package com.agent.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "conversation_messages", uniqueConstraints = @UniqueConstraint(
        name = "uk_conversation_message_client",
        columnNames = {"conversationId", "userId", "clientMessageId"}
))
public class AiConversationMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long conversationId;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false, length = 16) private String role;
    @Column(nullable = false, length = 12000) private String content;
    private Long modelId;
    @Column(nullable = false) private long inputTokens;
    @Column(nullable = false) private long outputTokens;
    @Column(nullable = false) private long totalTokens;
    @Column(nullable = false, length = 20) private String status;
    @Column(length = 80) private String clientMessageId;
    @Column(nullable = false) private Instant createdAt;

    protected AiConversationMessage() { }
    public AiConversationMessage(Long conversationId, Long userId, String role, String content, Long modelId, long inputTokens, long outputTokens, String status) {
        this(conversationId, userId, role, content, modelId, inputTokens, outputTokens, status, null);
    }
    public AiConversationMessage(Long conversationId, Long userId, String role, String content, Long modelId, long inputTokens, long outputTokens, String status, String clientMessageId) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.role = role;
        this.content = content == null ? "" : content;
        this.modelId = modelId;
        this.inputTokens = Math.max(0, inputTokens);
        this.outputTokens = Math.max(0, outputTokens);
        this.totalTokens = this.inputTokens + this.outputTokens;
        this.status = status;
        this.clientMessageId = clientMessageId;
        this.createdAt = Instant.now();
    }
    public void complete(String content, Long modelId, long inputTokens, long outputTokens) {
        this.content = content == null ? "" : content.trim();
        this.modelId = modelId;
        this.inputTokens = Math.max(0, inputTokens);
        this.outputTokens = Math.max(0, outputTokens);
        this.totalTokens = this.inputTokens + this.outputTokens;
        this.status = "COMPLETED";
    }
    public void markFailed() { markFailed(null); }
    public void markFailed(String safeMessage) {
        if (safeMessage != null && !safeMessage.isBlank()) this.content = safeMessage.trim();
        this.status = "FAILED";
    }
    public void markInvalid() { this.status = "INVALID"; }
    public void replaceContent(String content) { this.content = content == null ? "" : content.trim(); }
    public void markSuperseded() { this.status = "SUPERSEDED"; }
    public void markInterrupted(String partialContent) {
        this.content = partialContent == null ? "" : partialContent.trim();
        this.status = "INTERRUPTED";
    }
    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public Long getUserId() { return userId; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public Long getModelId() { return modelId; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public long getTotalTokens() { return totalTokens; }
    public String getStatus() { return status; }
    public String getClientMessageId() { return clientMessageId; }
    public Instant getCreatedAt() { return createdAt; }
}
