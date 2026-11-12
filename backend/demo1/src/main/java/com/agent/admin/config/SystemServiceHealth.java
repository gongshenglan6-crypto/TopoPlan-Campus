package com.agent.admin.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** Stores the latest durable health observation for each monitored component. */
@Entity
@Table(name = "system_service_health", uniqueConstraints = @UniqueConstraint(
        name = "uk_system_service_health_key", columnNames = "service_key"))
public class SystemServiceHealth {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_key", nullable = false, length = 80)
    private String serviceKey;

    @Column(nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(nullable = false, length = 500)
    private String detail;

    private Long responseTimeMs;
    private Double errorRate;
    private Double cpuPercent;
    private Double memoryPercent;

    @Column(nullable = false)
    private Instant checkedAt;

    @Column(nullable = false)
    private Instant statusChangedAt;

    protected SystemServiceHealth() {
    }

    public SystemServiceHealth(String serviceKey, String displayName, String status, String detail,
                               Long responseTimeMs, Double errorRate, Double cpuPercent, Double memoryPercent,
                               Instant checkedAt) {
        this.serviceKey = serviceKey;
        this.displayName = displayName;
        this.status = status;
        this.detail = detail;
        this.responseTimeMs = responseTimeMs;
        this.errorRate = errorRate;
        this.cpuPercent = cpuPercent;
        this.memoryPercent = memoryPercent;
        this.checkedAt = checkedAt;
        this.statusChangedAt = checkedAt;
    }

    public void update(String displayName, String status, String detail, Long responseTimeMs, Double errorRate,
                       Double cpuPercent, Double memoryPercent, Instant checkedAt) {
        if (!this.status.equals(status)) this.statusChangedAt = checkedAt;
        this.displayName = displayName;
        this.status = status;
        this.detail = detail;
        this.responseTimeMs = responseTimeMs;
        this.errorRate = errorRate;
        this.cpuPercent = cpuPercent;
        this.memoryPercent = memoryPercent;
        this.checkedAt = checkedAt;
    }

    public Long getId() { return id; }
    public String getServiceKey() { return serviceKey; }
    public String getDisplayName() { return displayName; }
    public String getStatus() { return status; }
    public String getDetail() { return detail; }
    public Long getResponseTimeMs() { return responseTimeMs; }
    public Double getErrorRate() { return errorRate; }
    public Double getCpuPercent() { return cpuPercent; }
    public Double getMemoryPercent() { return memoryPercent; }
    public Instant getCheckedAt() { return checkedAt; }
    public Instant getStatusChangedAt() { return statusChangedAt; }
}
