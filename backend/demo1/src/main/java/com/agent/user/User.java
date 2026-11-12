package com.agent.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String username;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 120)
    private String displayName;

    @Column(unique = true, length = 180)
    private String email;

    @Lob
    @Column
    private String avatar;

    @Column(nullable = false, length = 16)
    private String role = "USER";

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(nullable = false, length = 40)
    private String tier = "FREE";

    @Column(nullable = false)
    private int dailyQuota = 0;

    @Column(nullable = false)
    private long tokenVersion = 0L;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;
    private Instant lastLoginAt;

    private Instant subscriptionStartedAt;
    private Instant subscriptionExpiresAt;

    protected User() {
    }

    public User(String username, String passwordHash, String displayName, String email, String role) {
        this(username, passwordHash, displayName, email, null, role);
    }

    public User(String username, String passwordHash, String displayName, String email, String avatar, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.email = email;
        this.avatar = avatar;
        this.role = role;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.subscriptionStartedAt = this.createdAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public void markLogin() {
        this.lastLoginAt = Instant.now();
        touch();
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getAvatar() { return avatar; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public String getTier() { return tier; }
    public int getDailyQuota() { return dailyQuota; }
    public long getTokenVersion() { return tokenVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Instant getSubscriptionStartedAt() { return subscriptionStartedAt; }
    public Instant getSubscriptionExpiresAt() { return subscriptionExpiresAt; }

    public void setUsername(String username) { this.username = username; touch(); }
    public void setDisplayName(String displayName) { this.displayName = displayName; touch(); }
    public void setEmail(String email) { this.email = email; touch(); }
    public void setAvatar(String avatar) { this.avatar = avatar; touch(); }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; touch(); }
    public void setStatus(String status) { this.status = status; touch(); }
    public void setRole(String role) { this.role = role; touch(); }
    public void setTier(String tier) { this.tier = tier; touch(); }
    public void setDailyQuota(int dailyQuota) { this.dailyQuota = dailyQuota; touch(); }
    public void incrementTokenVersion() { this.tokenVersion += 1; touch(); }
    public void setSubscriptionStartedAt(Instant subscriptionStartedAt) { this.subscriptionStartedAt = subscriptionStartedAt; touch(); }
    public void setSubscriptionExpiresAt(Instant subscriptionExpiresAt) { this.subscriptionExpiresAt = subscriptionExpiresAt; touch(); }
}
