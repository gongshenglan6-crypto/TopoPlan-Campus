package com.agent.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chat_sessions")
public class ChatSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false)
    private boolean pinned;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ChatSession() {
    }

    public ChatSession(Long ownerId, Long projectId, String title) {
        this.ownerId = ownerId;
        this.projectId = projectId;
        this.title = title;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void touch() { this.updatedAt = Instant.now(); }
    public void update(String title, Boolean pinned) {
        if (title != null && !title.isBlank()) this.title = title.trim();
        if (pinned != null) this.pinned = pinned;
        touch();
    }
    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public Long getProjectId() { return projectId; }
    public String getTitle() { return title; }
    public boolean isPinned() { return pinned; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
