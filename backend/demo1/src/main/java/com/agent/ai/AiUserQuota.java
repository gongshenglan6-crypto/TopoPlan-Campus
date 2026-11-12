package com.agent.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ai_user_quotas")
public class AiUserQuota {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private Long userId;
    @Column(nullable = false) private int aiChatRemaining;
    @Column(nullable = false) private int topologyGenerateRemaining;
    @Column(nullable = false) private int requirementAnalysisRemaining;
    @Column(nullable = false) private int documentGenerateRemaining;
    @Column(nullable = false) private int webSearchRemaining;
    @Column(nullable = false) private long monthlyTokenLimit;
    @Column(nullable = false) private long usedInputTokens;
    @Column(nullable = false) private long usedOutputTokens;
    @Column(nullable = false) private long totalUsedTokens;
    @Column(nullable = false, precision = 18, scale = 8) private BigDecimal currentMonthCost;
    @Column(nullable = false) private Instant resetAt;
    @Column(nullable = false) private Instant updatedAt;
    @Column private Integer rpmLimit;
    @Column private Long tpmLimit;
    @Column private Integer customDailyCalls;
    @Column(length = 2000) private String allowedModelIds;
    // Defaults keep ddl-auto=update compatible with rows created before reservations existed.
    @Column(nullable = false, columnDefinition = "bigint default 0") private long reservedTokens;
    @Column(nullable = false, columnDefinition = "int default 0") private int reservedAiCalls;
    @Column(nullable = false, columnDefinition = "int default 0") private int reservedTopologyCalls;
    @Column(nullable = false, columnDefinition = "int default 0") private int reservedKnowledgeQaCalls;
    @Column(nullable = false, columnDefinition = "int default 0") private int reservedNetworkPlanCalls;

    protected AiUserQuota() { }
    public AiUserQuota(Long userId, int aiChatRemaining) {
        this.userId = userId;
        this.aiChatRemaining = Math.max(0, aiChatRemaining);
        this.topologyGenerateRemaining = aiChatRemaining;
        this.requirementAnalysisRemaining = aiChatRemaining;
        this.documentGenerateRemaining = aiChatRemaining;
        this.webSearchRemaining = aiChatRemaining;
        this.monthlyTokenLimit = Math.max(1_000_000L, (long) aiChatRemaining * 100_000L);
        this.currentMonthCost = BigDecimal.ZERO;
        this.resetAt = Instant.now().plusSeconds(30L * 24 * 60 * 60);
        this.updatedAt = Instant.now();
        this.rpmLimit = 20;
        this.tpmLimit = 100_000L;
        this.customDailyCalls = 0;
        this.allowedModelIds = "";
        this.reservedTokens = 0;
        this.reservedAiCalls = 0;
        this.reservedTopologyCalls = 0;
        this.reservedKnowledgeQaCalls = 0;
        this.reservedNetworkPlanCalls = 0;
    }
    public void reserveChat() { aiChatRemaining--; touch(); }
    public void refundChat() { aiChatRemaining++; touch(); }
    public void recordUsage(long inputTokens, long outputTokens, BigDecimal totalCost) {
        usedInputTokens += Math.max(0, inputTokens);
        usedOutputTokens += Math.max(0, outputTokens);
        totalUsedTokens += Math.max(0, inputTokens) + Math.max(0, outputTokens);
        currentMonthCost = currentMonthCost.add(totalCost == null ? BigDecimal.ZERO : totalCost);
        touch();
    }
    public void applyAdminValues(Integer chatRemaining, Long tokenLimit) {
        applyAdminValues(chatRemaining, tokenLimit, null, null, null, null);
    }
    public void applyAdminValues(Integer chatRemaining, Long tokenLimit, Integer rpm, Long tpm,
                                 Integer extraDailyCalls, Object modelIds) {
        if (chatRemaining != null) aiChatRemaining = Math.max(0, chatRemaining);
        if (tokenLimit != null) monthlyTokenLimit = Math.max(0, tokenLimit);
        if (rpm != null) rpmLimit = Math.max(1, rpm);
        if (tpm != null) tpmLimit = Math.max(1L, tpm);
        if (extraDailyCalls != null) customDailyCalls = Math.max(0, extraDailyCalls);
        if (modelIds != null) {
            if (modelIds instanceof java.util.Collection<?> values) {
                allowedModelIds = values.stream().map(String::valueOf).map(String::trim)
                        .filter(value -> !value.isBlank()).distinct()
                        .reduce((left, right) -> left + "," + right).orElse("");
            } else {
                allowedModelIds = String.valueOf(modelIds).trim();
            }
        }
        touch();
    }
    private void touch() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public int getAiChatRemaining() { return aiChatRemaining; }
    public int getTopologyGenerateRemaining() { return topologyGenerateRemaining; }
    public int getRequirementAnalysisRemaining() { return requirementAnalysisRemaining; }
    public int getDocumentGenerateRemaining() { return documentGenerateRemaining; }
    public int getWebSearchRemaining() { return webSearchRemaining; }
    public long getMonthlyTokenLimit() { return monthlyTokenLimit; }
    public long getUsedInputTokens() { return usedInputTokens; }
    public long getUsedOutputTokens() { return usedOutputTokens; }
    public long getTotalUsedTokens() { return totalUsedTokens; }
    public BigDecimal getCurrentMonthCost() { return currentMonthCost; }
    public Instant getResetAt() { return resetAt; }
    public int getRpmLimit() { return rpmLimit == null ? 20 : rpmLimit; }
    public long getTpmLimit() { return tpmLimit == null ? 100_000L : tpmLimit; }
    public int getCustomDailyCalls() { return customDailyCalls == null ? 0 : customDailyCalls; }
    public java.util.List<String> getAllowedModelIds() {
        if (allowedModelIds == null || allowedModelIds.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(allowedModelIds.split(",")).map(String::trim)
                .filter(value -> !value.isBlank()).toList();
    }
    public long getReservedTokens() { return Math.max(0, reservedTokens); }
    public long reservedCalls(String businessType) {
        return switch (businessType == null ? "chat" : businessType) {
            case "topology_generation" -> Math.max(0, reservedTopologyCalls);
            case "knowledge_qa" -> Math.max(0, reservedKnowledgeQaCalls);
            case "network_plan" -> Math.max(0, reservedNetworkPlanCalls);
            default -> Math.max(0, reservedAiCalls);
        };
    }
    public void reserve(String businessType, long tokens) {
        switch (businessType == null ? "chat" : businessType) {
            case "topology_generation" -> reservedTopologyCalls++;
            case "knowledge_qa" -> reservedKnowledgeQaCalls++;
            case "network_plan" -> reservedNetworkPlanCalls++;
            default -> reservedAiCalls++;
        }
        reservedTokens += Math.max(0, tokens);
        touch();
    }
    public void release(String businessType, long tokens) {
        switch (businessType == null ? "chat" : businessType) {
            case "topology_generation" -> reservedTopologyCalls = Math.max(0, reservedTopologyCalls - 1);
            case "knowledge_qa" -> reservedKnowledgeQaCalls = Math.max(0, reservedKnowledgeQaCalls - 1);
            case "network_plan" -> reservedNetworkPlanCalls = Math.max(0, reservedNetworkPlanCalls - 1);
            default -> reservedAiCalls = Math.max(0, reservedAiCalls - 1);
        }
        reservedTokens = Math.max(0, reservedTokens - Math.max(0, tokens));
        touch();
    }
}
