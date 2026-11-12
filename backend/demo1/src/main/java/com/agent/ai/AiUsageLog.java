package com.agent.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Index;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ai_usage_logs", indexes = {
        @Index(name = "idx_ai_usage_logs_created_at", columnList = "createdAt"),
        @Index(name = "idx_ai_usage_logs_user_created_at", columnList = "userId,createdAt"),
        @Index(name = "idx_ai_usage_logs_business_created_at", columnList = "businessType,createdAt")
})
public class AiUsageLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 64) private String requestId;
    @Column(nullable = false, length = 64) private String traceId;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private Long modelId;
    @Column(nullable = false, length = 80) private String provider;
    @Column(nullable = false, length = 64) private String businessType;
    private Long conversationId;
    @Column(nullable = false) private long inputTokens;
    @Column(nullable = false) private long outputTokens;
    @Column(nullable = false) private long totalTokens;
    @Column(nullable = false, precision = 18, scale = 8) private BigDecimal inputUnitPrice;
    @Column(nullable = false, precision = 18, scale = 8) private BigDecimal outputUnitPrice;
    @Column(nullable = false, precision = 18, scale = 8) private BigDecimal inputCost;
    @Column(nullable = false, precision = 18, scale = 8) private BigDecimal outputCost;
    @Column(nullable = false, precision = 18, scale = 8) private BigDecimal totalCost;
    @Column(nullable = false, length = 8) private String currency;
    @Column(nullable = false) private long latencyMs;
    @Column(nullable = false, length = 20) private String status;
    @Column(length = 80) private String errorCode;
    @Column(nullable = false) private Instant createdAt;

    protected AiUsageLog() { }
    public AiUsageLog(String requestId, String traceId, Long userId, Long modelId, String provider, String businessType,
                      Long conversationId, long inputTokens, long outputTokens, BigDecimal inputUnitPrice,
                      BigDecimal outputUnitPrice, BigDecimal inputCost, BigDecimal outputCost, String currency,
                      long latencyMs, String status, String errorCode) {
        this.requestId = requestId;
        this.traceId = traceId;
        this.userId = userId;
        this.modelId = modelId;
        this.provider = provider;
        this.businessType = businessType;
        this.conversationId = conversationId;
        this.inputTokens = Math.max(0, inputTokens);
        this.outputTokens = Math.max(0, outputTokens);
        this.totalTokens = this.inputTokens + this.outputTokens;
        this.inputUnitPrice = inputUnitPrice;
        this.outputUnitPrice = outputUnitPrice;
        this.inputCost = inputCost;
        this.outputCost = outputCost;
        this.totalCost = inputCost.add(outputCost);
        this.currency = currency;
        this.latencyMs = latencyMs;
        this.status = status;
        this.errorCode = errorCode;
        this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public String getRequestId() { return requestId; }
    public Long getUserId() { return userId; }
    public Long getModelId() { return modelId; }
    public String getProvider() { return provider; }
    public String getBusinessType() { return businessType; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public long getTotalTokens() { return totalTokens; }
    public BigDecimal getTotalCost() { return totalCost; }
    public String getCurrency() { return currency; }
    public long getLatencyMs() { return latencyMs; }
    public String getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public Instant getCreatedAt() { return createdAt; }
}
