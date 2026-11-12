package com.agent.login;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "login_records", indexes = {
        @Index(name = "idx_login_records_created_at", columnList = "created_at"),
        @Index(name = "idx_login_records_user_id", columnList = "user_id")
})
public class LoginRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 180)
    private String loginIdentifier;

    @Column(nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false, length = 32)
    private String accountType;

    @Column(nullable = false, length = 32)
    private String loginMethod;

    @Column(nullable = false, length = 64)
    private String ipAddress;

    @Column(nullable = false, length = 120)
    private String location;

    @Column(nullable = false, length = 120)
    private String device;

    @Column(nullable = false, length = 120)
    private String operatingSystem;

    @Column(nullable = false, length = 120)
    private String browser;

    @Lob
    @Column
    private String userAgent;

    @Column(nullable = false, length = 16)
    private String result;

    @Column(nullable = false, length = 16)
    private String riskStatus;

    @Column(length = 255)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LoginRecord() {
    }

    public LoginRecord(Long userId, String loginIdentifier, String displayName, String accountType, String loginMethod,
                       String ipAddress, String location, String device, String operatingSystem, String browser,
                       String userAgent, String result, String riskStatus, String failureReason) {
        this.userId = userId;
        this.loginIdentifier = loginIdentifier;
        this.displayName = displayName;
        this.accountType = accountType;
        this.loginMethod = loginMethod;
        this.ipAddress = ipAddress;
        this.location = location;
        this.device = device;
        this.operatingSystem = operatingSystem;
        this.browser = browser;
        this.userAgent = userAgent;
        this.result = result;
        this.riskStatus = riskStatus;
        this.failureReason = failureReason;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getLoginIdentifier() { return loginIdentifier; }
    public String getDisplayName() { return displayName; }
    public String getAccountType() { return accountType; }
    public String getLoginMethod() { return loginMethod; }
    public String getIpAddress() { return ipAddress; }
    public String getLocation() { return location; }
    public String getDevice() { return device; }
    public String getOperatingSystem() { return operatingSystem; }
    public String getBrowser() { return browser; }
    public String getUserAgent() { return userAgent; }
    public String getResult() { return result; }
    public String getRiskStatus() { return riskStatus; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
}
