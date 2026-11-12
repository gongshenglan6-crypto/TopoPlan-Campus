package com.agent.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ai_models")
public class AiModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String providerName;

    @Column(nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false, length = 160)
    private String modelId;

    @Column(nullable = false, length = 32)
    private String modelType;

    @Column(nullable = false, length = 500)
    private String baseUrl;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedApiKey;

    @Column(nullable = false, length = 24)
    private String status = "ACTIVE";

    @Column(nullable = false)
    private int contextWindow;

    @Column(nullable = false)
    private int maxOutputTokens;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal temperature;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal topP;

    @Column(nullable = false)
    private int maxConcurrency;

    @Column(nullable = false)
    private int currentConcurrency;

    @Column(nullable = false)
    private int rpmLimit;

    @Column(nullable = false)
    private long tpmLimit;

    @Column(nullable = false)
    private int timeoutSeconds;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal inputTokenPrice;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal outputTokenPrice;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal cachedTokenPrice;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal quotaMultiplier;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(nullable = false)
    private long priceUnit;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private int priority;

    @Column(length = 16)
    private String healthStatus = "HEALTHY";
    private Instant lastSuccessAt;
    private Instant lastFailureAt;
    private Integer consecutiveFailures = 0;
    @Column(length = 80)
    private String lastErrorCode;
    private Instant healthCooldownUntil;

    @Column(nullable = false)
    private boolean userSelectable = true;

    @Column(nullable = false)
    private long lastLatencyMs;

    @Column(nullable = false, length = 24, columnDefinition = "varchar(24) default 'NOT_TESTED'")
    private String lastTestStatus = "NOT_TESTED";

    private Instant lastTestAt;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long lastTestLatencyMs;

    @Column(length = 80)
    private String lastTestErrorCode;

    @Column(length = 1000)
    private String lastTestErrorMessage;

    @Column(nullable = false, length = 300)
    private String allowedTiers = "free,standard,pro";

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Soft-delete marker. Historical usage records can continue to reference this model. */
    private Instant deletedAt;

    protected AiModel() {
    }

    public AiModel(String providerName, String displayName, String modelId, String modelType, String baseUrl,
                   String encryptedApiKey) {
        this.providerName = providerName;
        this.displayName = displayName;
        this.modelId = modelId;
        this.modelType = modelType;
        this.baseUrl = baseUrl;
        this.encryptedApiKey = encryptedApiKey;
        this.contextWindow = 128000;
        this.maxOutputTokens = 4096;
        this.temperature = new BigDecimal("0.7");
        this.topP = new BigDecimal("0.95");
        this.maxConcurrency = 10;
        this.currentConcurrency = 0;
        this.rpmLimit = 600;
        this.tpmLimit = 1_000_000L;
        this.timeoutSeconds = 60;
        this.inputTokenPrice = BigDecimal.ZERO;
        this.outputTokenPrice = BigDecimal.ZERO;
        this.cachedTokenPrice = BigDecimal.ZERO;
        this.quotaMultiplier = BigDecimal.ONE;
        this.currency = "USD";
        this.priceUnit = 1_000_000L;
        this.enabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void touch() { this.updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public String getProviderName() { return providerName; }
    public String getDisplayName() { return displayName; }
    public String getModelId() { return modelId; }
    public String getModelType() { return modelType; }
    public String getBaseUrl() { return baseUrl; }
    public String getEncryptedApiKey() { return encryptedApiKey; }
    public String getStatus() { return status; }
    public int getContextWindow() { return contextWindow; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public BigDecimal getTemperature() { return temperature; }
    public BigDecimal getTopP() { return topP; }
    public int getMaxConcurrency() { return maxConcurrency; }
    public int getCurrentConcurrency() { return currentConcurrency; }
    public int getRpmLimit() { return rpmLimit; }
    public long getTpmLimit() { return tpmLimit; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public BigDecimal getInputTokenPrice() { return inputTokenPrice; }
    public BigDecimal getOutputTokenPrice() { return outputTokenPrice; }
    public BigDecimal getCachedTokenPrice() { return cachedTokenPrice; }
    public BigDecimal getQuotaMultiplier() { return quotaMultiplier; }
    public String getCurrency() { return currency; }
    public long getPriceUnit() { return priceUnit; }
    public boolean isEnabled() { return enabled; }
    public int getPriority() { return priority; }
    public String getHealthStatus() { return healthStatus == null || healthStatus.isBlank() ? "HEALTHY" : healthStatus; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }
    public Instant getLastFailureAt() { return lastFailureAt; }
    public int getConsecutiveFailures() { return consecutiveFailures == null ? 0 : consecutiveFailures; }
    public String getLastErrorCode() { return lastErrorCode; }
    public Instant getHealthCooldownUntil() { return healthCooldownUntil; }
    public boolean isUserSelectable() { return userSelectable; }
    public long getLastLatencyMs() { return lastLatencyMs; }
    public String getLastTestStatus() { return lastTestStatus; }
    public Instant getLastTestAt() { return lastTestAt; }
    public long getLastTestLatencyMs() { return lastTestLatencyMs; }
    public String getLastTestErrorCode() { return lastTestErrorCode; }
    public String getLastTestErrorMessage() { return lastTestErrorMessage; }
    public String getAllowedTiers() { return allowedTiers; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setProviderName(String providerName) { this.providerName = providerName; touch(); }
    public void setDisplayName(String displayName) { this.displayName = displayName; touch(); }
    public void setModelId(String modelId) { this.modelId = modelId; touch(); }
    public void setModelType(String modelType) { this.modelType = modelType; touch(); }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; touch(); }
    public void setEncryptedApiKey(String encryptedApiKey) { this.encryptedApiKey = encryptedApiKey; touch(); }
    public void setStatus(String status) { this.status = status; touch(); }
    public void setContextWindow(int contextWindow) { this.contextWindow = contextWindow; touch(); }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; touch(); }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; touch(); }
    public void setTopP(BigDecimal topP) { this.topP = topP; touch(); }
    public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency; touch(); }
    public void setCurrentConcurrency(int currentConcurrency) { this.currentConcurrency = currentConcurrency; touch(); }
    public void setRpmLimit(int rpmLimit) { this.rpmLimit = rpmLimit; touch(); }
    public void setTpmLimit(long tpmLimit) { this.tpmLimit = tpmLimit; touch(); }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; touch(); }
    public void setInputTokenPrice(BigDecimal inputTokenPrice) { this.inputTokenPrice = inputTokenPrice; touch(); }
    public void setOutputTokenPrice(BigDecimal outputTokenPrice) { this.outputTokenPrice = outputTokenPrice; touch(); }
    public void setCachedTokenPrice(BigDecimal cachedTokenPrice) { this.cachedTokenPrice = cachedTokenPrice; touch(); }
    public void setQuotaMultiplier(BigDecimal quotaMultiplier) { this.quotaMultiplier = quotaMultiplier; touch(); }
    public void setCurrency(String currency) { this.currency = currency; touch(); }
    public void setPriceUnit(long priceUnit) { this.priceUnit = priceUnit; touch(); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; touch(); }
    public void setPriority(int priority) { this.priority = priority; touch(); }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; touch(); }
    public void setLastSuccessAt(Instant lastSuccessAt) { this.lastSuccessAt = lastSuccessAt; touch(); }
    public void setLastFailureAt(Instant lastFailureAt) { this.lastFailureAt = lastFailureAt; touch(); }
    public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = Math.max(0, consecutiveFailures); touch(); }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; touch(); }
    public void setHealthCooldownUntil(Instant healthCooldownUntil) { this.healthCooldownUntil = healthCooldownUntil; touch(); }
    public void setUserSelectable(boolean userSelectable) { this.userSelectable = userSelectable; touch(); }
    public void setLastLatencyMs(long lastLatencyMs) { this.lastLatencyMs = lastLatencyMs; touch(); }
    public void markTestRunning() { this.lastTestStatus = "TESTING"; this.lastTestAt = Instant.now(); this.lastTestLatencyMs = 0; this.lastTestErrorCode = null; this.lastTestErrorMessage = null; touch(); }
    public void markTestPassed(long latencyMs) { this.lastTestStatus = "PASSED"; this.lastTestAt = Instant.now(); this.lastTestLatencyMs = Math.max(0, latencyMs); this.lastLatencyMs = Math.max(0, latencyMs); this.lastTestErrorCode = null; this.lastTestErrorMessage = null; markHealthy(latencyMs); }
    public void markTestFailed(long latencyMs, String errorCode, String errorMessage) { this.lastTestStatus = "FAILED"; this.lastTestAt = Instant.now(); this.lastTestLatencyMs = Math.max(0, latencyMs); this.lastTestErrorCode = errorCode; this.lastTestErrorMessage = errorMessage; touch(); }
    public boolean ensureTestDefaults() { if (lastTestStatus == null || lastTestStatus.isBlank()) { lastTestStatus = "NOT_TESTED"; touch(); return true; } return false; }
    public void setAllowedTiers(String allowedTiers) { this.allowedTiers = allowedTiers; touch(); }
    public void setDescription(String description) { this.description = description; touch(); }
    public void markDeleted() { this.enabled = false; this.status = "DELETED"; this.deletedAt = Instant.now(); touch(); }

    public synchronized void markHealthy(long latencyMs) {
        healthStatus = "HEALTHY";
        lastSuccessAt = Instant.now();
        lastLatencyMs = Math.max(0, latencyMs);
        consecutiveFailures = 0;
        lastErrorCode = null;
        healthCooldownUntil = null;
        touch();
    }

    public synchronized void markRuntimeFailure(String errorCode, long latencyMs, int threshold, long cooldownSeconds) {
        Instant now = Instant.now();
        lastFailureAt = now;
        lastLatencyMs = Math.max(0, latencyMs);
        lastErrorCode = errorCode;
        consecutiveFailures = getConsecutiveFailures() + 1;
        if (consecutiveFailures >= Math.max(1, threshold)) {
            healthStatus = "OPEN";
            healthCooldownUntil = now.plusSeconds(Math.max(1, cooldownSeconds));
        } else {
            healthStatus = "DEGRADED";
        }
        touch();
    }

    public synchronized boolean runtimeAvailable(Instant now) {
        if (!"OPEN".equalsIgnoreCase(getHealthStatus())) return true;
        if (healthCooldownUntil == null || !healthCooldownUntil.isAfter(now)) {
            healthStatus = "HALF_OPEN";
            touch();
            return true;
        }
        return false;
    }
}
