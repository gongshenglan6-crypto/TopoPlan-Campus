package com.agent.admin.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "risk_request_counters", uniqueConstraints = @UniqueConstraint(
        name = "uk_risk_request_counter_ip_bucket", columnNames = {"ip_address", "bucket_start"}))
public class RiskRequestCounter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ip_address", nullable = false, length = 64)
    private String ipAddress;

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Column(name = "request_count", nullable = false)
    private long requestCount;

    @Column(name = "failure_count", nullable = false)
    private long failureCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RiskRequestCounter() { }

    public RiskRequestCounter(String ipAddress, Instant bucketStart) {
        this.ipAddress = ipAddress;
        this.bucketStart = bucketStart;
        this.requestCount = 0;
        this.failureCount = 0;
        this.updatedAt = Instant.now();
    }

    public long increment() {
        requestCount++;
        updatedAt = Instant.now();
        return requestCount;
    }

    public long incrementFailure() {
        failureCount++;
        updatedAt = Instant.now();
        return failureCount;
    }

    public Long getId() { return id; }
    public String getIpAddress() { return ipAddress; }
    public Instant getBucketStart() { return bucketStart; }
    public long getRequestCount() { return requestCount; }
    public long getFailureCount() { return failureCount; }
    public Instant getUpdatedAt() { return updatedAt; }
}
