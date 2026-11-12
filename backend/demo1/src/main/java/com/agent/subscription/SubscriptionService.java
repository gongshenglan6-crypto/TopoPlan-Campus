package com.agent.subscription;

import com.agent.admin.config.AdminConfigService;
import com.agent.ai.AiUsageLog;
import com.agent.ai.AiUsageLogRepository;
import com.agent.ai.AiUserQuota;
import com.agent.ai.AiUserQuotaRepository;
import com.agent.common.api.ApiException;
import com.agent.user.User;
import com.agent.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The database-backed source of truth for plan policy and per-user period usage. */
@Service
public class SubscriptionService {
    public static final String BILLING_MODULE = "billing.tiers";
    public static final List<String> PLAN_CODES = List.of("FREE", "STANDARD", "PRO");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final AdminConfigService configs;
    private final UserRepository users;
    private final AiUsageLogRepository usageLogs;
    private final AiUserQuotaRepository userQuotaRepository;

    public SubscriptionService(AdminConfigService configs, UserRepository users, AiUsageLogRepository usageLogs,
                               AiUserQuotaRepository userQuotaRepository) {
        this.configs = configs;
        this.users = users;
        this.usageLogs = usageLogs;
        this.userQuotaRepository = userQuotaRepository;
    }

    @Transactional
    public void initializePlansAndMigrateUsers() {
        migrateUsersAndPlans(true);
    }

    /** Migrates existing persisted records without creating plans in an intentionally empty production database. */
    @Transactional
    public void migrateExistingUsersAndPlans() {
        migrateUsersAndPlans(false);
    }

    private void migrateUsersAndPlans(boolean createMissingPlans) {
        Map<String, Map<String, Object>> existing = new LinkedHashMap<>();
        for (Map<String, Object> entry : configs.list(BILLING_MODULE)) {
            String code = canonicalTier(string(entry.getOrDefault("code", entry.get("key"))));
            if (!code.isBlank() && !existing.containsKey(code)) existing.put(code, entry);
        }
        for (String code : PLAN_CODES) {
            Map<String, Object> source = existing.get(code);
            if (source != null) {
                configs.save(BILLING_MODULE, code.toLowerCase(Locale.ROOT), normalizePlan(code, source));
            } else if (createMissingPlans) {
                configs.save(BILLING_MODULE, code.toLowerCase(Locale.ROOT), normalizePlan(code, defaultPlan(code)));
            }
        }
        for (Map<String, Object> entry : configs.list(BILLING_MODULE)) {
            String key = string(entry.get("key"));
            String code = canonicalTier(string(entry.getOrDefault("code", key)));
            if ((!PLAN_CODES.contains(code) || !key.equalsIgnoreCase(code.toLowerCase(Locale.ROOT))) && !key.isBlank()) {
                configs.delete(BILLING_MODULE, key);
            }
        }
        users.findAll().forEach(user -> {
            String normalized = canonicalTier(user.getTier());
            if (normalized.isBlank()) normalized = "FREE";
            if (!normalized.equals(user.getTier())) user.setTier(normalized);
            if (user.getSubscriptionStartedAt() == null) user.setSubscriptionStartedAt(user.getCreatedAt());
        });
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> plans() {
        return configs.list(BILLING_MODULE).stream()
                .map(entry -> {
                    String code = canonicalTier(string(entry.getOrDefault("code", entry.get("key"))));
                    if (!PLAN_CODES.contains(code)) return null;
                    Map<String, Object> plan = normalizePlan(code, entry);
                    plan.put("benefits", benefits(plan));
                    return plan;
                })
                .filter(java.util.Objects::nonNull)
                .filter(entry -> PLAN_CODES.contains(string(entry.get("code"))))
                .sorted(Comparator.comparingInt(entry -> PLAN_CODES.indexOf(string(entry.get("code")))))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> planFor(String tier) {
        String code = canonicalTier(tier);
        return plans().stream().filter(plan -> code.equals(plan.get("code"))).findFirst().orElse(null);
    }

    @Transactional
    public Map<String, Object> savePlan(String requestedCode, Map<String, Object> values) {
        String code = canonicalTier(requestedCode);
        if (!PLAN_CODES.contains(code)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PLAN", "套餐只允许为 FREE、STANDARD 或 PRO");
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        Map<String, Object> current = planFor(code);
        if (current != null) merged.putAll(current);
        if (values != null) merged.putAll(values);
        Map<String, Object> normalized = normalizePlan(code, merged);
        return normalizePlan(code, configs.save(BILLING_MODULE, code.toLowerCase(Locale.ROOT), normalized));
    }

    @Transactional
    public void assignTier(User user, String requestedTier) {
        String tier = canonicalTier(requestedTier);
        if (!PLAN_CODES.contains(tier)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_USER_LEVEL", "用户等级只允许为 FREE、STANDARD 或 PRO");
        }
        if (!tier.equals(user.getTier())) {
            user.setTier(tier);
            user.setSubscriptionStartedAt(Instant.now());
            user.setSubscriptionExpiresAt(null);
        } else if (user.getSubscriptionStartedAt() == null) {
            user.setSubscriptionStartedAt(user.getCreatedAt());
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> subscriptionFor(User user) {
        Map<String, Object> plan = planFor(user.getTier());
        if (plan == null) {
            return missingPlanSubscription(user);
        }
        UsagePeriod period = currentPeriod();
        List<AiUsageLog> logs = usageLogs.findByUserIdAndCreatedAtGreaterThanEqual(user.getId(), period.startsAt());
        Usage usage = Usage.from(logs);
        AiUserQuota adjustment = userQuotaRepository.findByUserId(user.getId()).orElse(null);
        long extraAiCalls = adjustment == null ? 0 : Math.max(0, adjustment.getCustomDailyCalls());
        Map<String, Object> quotas = quotaValues(plan, extraAiCalls);
        Map<String, Object> usageValues = usage.toMap();
        Map<String, Object> remaining = remainingValues(quotas, usage);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("level", plan.get("code"));
        result.put("levelName", plan.get("backendName"));
        result.put("planName", plan.get("userName"));
        result.put("status", "ACTIVE".equalsIgnoreCase(user.getStatus()) ? "ACTIVE" : "SUSPENDED");
        result.put("startAt", user.getSubscriptionStartedAt() == null ? user.getCreatedAt() : user.getSubscriptionStartedAt());
        result.put("expireAt", user.getSubscriptionExpiresAt());
        result.put("period", plan.get("period"));
        result.put("nextResetAt", period.endsAt());
        result.put("quota", quotas);
        result.put("usage", usageValues);
        result.put("remaining", remaining);
        result.put("benefits", benefits(plan));
        result.put("plan", plan);
        result.put("additionalAiCalls", extraAiCalls);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminUserSummary(User user) {
        Map<String, Object> subscription = subscriptionFor(user);
        Map<String, Object> quota = map(subscription.get("quota"));
        Map<String, Object> usage = map(subscription.get("usage"));
        Map<String, Object> remaining = map(subscription.get("remaining"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subscription", subscription);
        result.put("tier", subscription.get("level"));
        result.put("aiChatTotal", number(map(quota.get("aiChat")).get("total")));
        result.put("aiChatUsed", number(map(usage.get("aiChat")).get("used")));
        result.put("aiChatRemaining", number(map(remaining.get("aiChat")).get("value")));
        result.put("topologyTotal", number(map(quota.get("topologyGeneration")).get("total")));
        result.put("topologyUsed", number(map(usage.get("topologyGeneration")).get("used")));
        result.put("topologyRemaining", number(map(remaining.get("topologyGeneration")).get("value")));
        result.put("tokenTotal", number(map(quota.get("tokens")).get("total")));
        result.put("tokenUsed", number(map(usage.get("tokens")).get("used")));
        result.put("tokenRemaining", number(map(remaining.get("tokens")).get("value")));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminQuotaRows() {
        return users.findAll().stream().map(user -> {
            Map<String, Object> summary = adminUserSummary(user);
            Map<String, Object> subscription = map(summary.get("subscription"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", user.getId());
            row.put("userId", user.getId());
            row.put("username", user.getUsername());
            row.put("displayName", user.getDisplayName());
            row.put("tier", summary.get("tier"));
            row.put("subscription", subscription);
            row.put("aiChatTotal", summary.get("aiChatTotal"));
            row.put("aiChatUsed", summary.get("aiChatUsed"));
            row.put("aiChatRemaining", summary.get("aiChatRemaining"));
            row.put("topologyTotal", summary.get("topologyTotal"));
            row.put("topologyUsed", summary.get("topologyUsed"));
            row.put("topologyRemaining", summary.get("topologyRemaining"));
            row.put("monthlyTokenLimit", summary.get("tokenTotal"));
            row.put("totalUsedTokens", summary.get("tokenUsed"));
            row.put("tokenRemaining", summary.get("tokenRemaining"));
            row.put("resetAt", subscription.get("nextResetAt"));
            AiUserQuota adjustment = userQuotaRepository.findByUserId(user.getId()).orElse(null);
            row.put("rpm", adjustment == null ? null : adjustment.getRpmLimit());
            row.put("tpm", adjustment == null ? null : adjustment.getTpmLimit());
            row.put("customDailyCalls", adjustment == null ? 0 : adjustment.getCustomDailyCalls());
            row.put("allowedModels", adjustment == null ? List.of() : adjustment.getAllowedModelIds());
            return row;
        }).toList();
    }

    @Transactional(readOnly = true)
    public QuotaCheck quotaCheck(User user, String businessType, long estimatedInputTokens) {
        Map<String, Object> subscription = subscriptionFor(user);
        if (Boolean.TRUE.equals(subscription.get("planMissing"))) {
            throw new ApiException(HttpStatus.CONFLICT, "PLAN_NOT_CONFIGURED", "当前用户等级尚未配置套餐额度");
        }
        Map<String, Object> quota = map(subscription.get("quota"));
        Map<String, Object> usage = map(subscription.get("usage"));
        String key = quotaKeyForBusinessType(businessType);
        Map<String, Object> featureQuota = map(quota.get(key));
        Map<String, Object> featureUsage = map(usage.get(key));
        long total = number(featureQuota.get("total"));
        long used = number(featureUsage.get("used"));
        long tokensTotal = number(map(quota.get("tokens")).get("total"));
        long tokensUsed = number(map(usage.get("tokens")).get("used"));
        if (total <= used) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, quotaErrorCode(key), "本周期 " + string(featureQuota.get("label")) + "额度已用完",
                    quotaErrorDetails(subscription, featureQuota, used, total, 0));
        }
        if (tokensTotal <= tokensUsed + Math.max(0, estimatedInputTokens)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOKEN_QUOTA_EXCEEDED", "本周期 Token 额度不足",
                    quotaErrorDetails(subscription, map(quota.get("tokens")), tokensUsed, tokensTotal, estimatedInputTokens));
        }
        return new QuotaCheck(total, used, tokensTotal, tokensUsed, subscription.get("nextResetAt"));
    }

    public static String canonicalTier(String value) {
        String identity = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (identity) {
            case "", "FREE", "BASIC", "NORMAL", "FREE_USER", "免费", "免费用户" -> "FREE";
            case "STANDARD", "TEAM", "EDUCATION", "普通", "普通用户", "标准", "标准用户" -> "STANDARD";
            case "PRO", "PREMIUM", "ADVANCED", "ENTERPRISE", "企业", "企业版", "企业用户", "高级", "高级用户", "专业", "专业版" -> "PRO";
            default -> "";
        };
    }

    private Map<String, Object> normalizePlan(String code, Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>(source == null ? Map.of() : source);
        String safeCode = PLAN_CODES.contains(code) ? code : "FREE";
        Map<String, Object> defaults = defaultPlan(safeCode);
        defaults.forEach(result::putIfAbsent);
        result.put("id", safeCode.toLowerCase(Locale.ROOT));
        result.put("code", safeCode);
        String configuredName = string(result.get("name"));
        String backendName = string(result.get("backendName"));
        String userName = string(result.get("userName"));
        result.put("backendName", backendName.isBlank() ? defaults.get("backendName") : backendName);
        result.put("userName", userName.isBlank() ? defaults.get("userName") : userName);
        result.put("name", configuredName.isBlank() ? result.get("backendName") : configuredName);
        // FREE is the system default used by registration and therefore can
        // never be disabled through admin configuration.
        result.put("enabled", "FREE".equals(safeCode) || Boolean.FALSE != result.get("enabled"));
        String period = string(result.get("period")).toUpperCase(Locale.ROOT);
        result.put("period", period.isBlank() ? "MONTHLY" : period);
        return result;
    }

    private Map<String, Object> defaultPlan(String code) {
        Map<String, Object> value = new LinkedHashMap<>();
        switch (code) {
            case "STANDARD" -> {
                value.put("backendName", "普通用户"); value.put("userName", "普通版"); value.put("description", "适用于常规网络工程规划工作。");
                value.put("aiCallQuota", 300); value.put("topologyQuota", 30); value.put("knowledgeQaQuota", 120); value.put("networkPlanQuota", 60); value.put("tokenQuota", 300000L);
                value.put("dailyCallLimit", 30); value.put("projectLimit", 30); value.put("knowledgeCapacityMb", 2048L); value.put("fileUploadLimit", 200); value.put("singleFileSizeMb", 50);
            }
            case "PRO" -> {
                value.put("backendName", "高级用户"); value.put("userName", "专业版"); value.put("description", "适用于专业网络工程设计与高频 AI 分析。");
                value.put("aiCallQuota", 1000); value.put("topologyQuota", 100); value.put("knowledgeQaQuota", 500); value.put("networkPlanQuota", 200); value.put("tokenQuota", 1000000L);
                value.put("dailyCallLimit", 120); value.put("projectLimit", 100); value.put("knowledgeCapacityMb", 10240L); value.put("fileUploadLimit", 1000); value.put("singleFileSizeMb", 100);
            }
            default -> {
                value.put("backendName", "免费用户"); value.put("userName", "免费版"); value.put("description", "适用于基础网络拓扑规划与体验。");
                value.put("aiCallQuota", 20); value.put("topologyQuota", 5); value.put("knowledgeQaQuota", 20); value.put("networkPlanQuota", 5); value.put("tokenQuota", 100000L);
                value.put("dailyCallLimit", 5); value.put("projectLimit", 5); value.put("knowledgeCapacityMb", 512L); value.put("fileUploadLimit", 20); value.put("singleFileSizeMb", 20);
            }
        }
        value.put("enabled", true);
        value.put("period", "MONTHLY");
        value.put("permissions", Map.of());
        return value;
    }

    private Map<String, Object> quotaValues(Map<String, Object> plan, long extraAiCalls) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("aiChat", quota("AI对话", number(plan.get("aiCallQuota")) + extraAiCalls));
        result.put("tokens", quota("Token", number(plan.get("tokenQuota"))));
        result.put("topologyGeneration", quota("AI拓扑生成", number(plan.get("topologyQuota"))));
        result.put("knowledgeQa", quota("知识库AI问答", number(plan.get("knowledgeQaQuota"))));
        result.put("networkPlan", quota("AI方案生成", number(plan.get("networkPlanQuota"))));
        result.put("projects", quota("项目数量", number(plan.get("projectLimit"))));
        result.put("knowledgeCapacityMb", quota("知识库容量", number(plan.get("knowledgeCapacityMb"))));
        result.put("fileUploads", quota("文件上传数量", number(plan.get("fileUploadLimit"))));
        result.put("singleFileSizeMb", quota("单文件大小", number(plan.get("singleFileSizeMb"))));
        result.put("dailyCallLimit", quota("每日调用限制", number(plan.get("dailyCallLimit"))));
        return result;
    }

    private Map<String, Object> remainingValues(Map<String, Object> quotas, Usage usage) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("aiChat", remaining(map(quotas.get("aiChat")), usage.aiChat));
        result.put("tokens", remaining(map(quotas.get("tokens")), usage.tokens));
        result.put("topologyGeneration", remaining(map(quotas.get("topologyGeneration")), usage.topologyGeneration));
        result.put("knowledgeQa", remaining(map(quotas.get("knowledgeQa")), usage.knowledgeQa));
        result.put("networkPlan", remaining(map(quotas.get("networkPlan")), usage.networkPlan));
        return result;
    }

    private Map<String, String> quotaErrorDetails(Map<String, Object> subscription, Map<String, Object> quota,
                                                   long used, long total, long estimatedInputTokens) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("planName", string(subscription.get("planName")));
        details.put("planLevel", string(subscription.get("level")));
        details.put("quotaLabel", string(quota.get("label")));
        details.put("used", String.valueOf(Math.max(0, used)));
        details.put("total", String.valueOf(Math.max(0, total)));
        details.put("remaining", String.valueOf(Math.max(0, total - used)));
        details.put("nextResetAt", string(subscription.get("nextResetAt")));
        if (estimatedInputTokens > 0) details.put("estimatedInputTokens", String.valueOf(estimatedInputTokens));
        return details;
    }

    private Map<String, Object> benefits(Map<String, Object> plan) {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(benefit("每月 AI 调用", number(plan.get("aiCallQuota")), "次"));
        result.add(benefit("每月 Token", number(plan.get("tokenQuota")), "Token"));
        result.add(benefit("AI 拓扑生成", number(plan.get("topologyQuota")), "次"));
        result.add(benefit("知识库 AI 问答", number(plan.get("knowledgeQaQuota")), "次"));
        result.add(benefit("最多创建项目", number(plan.get("projectLimit")), "个"));
        result.add(benefit("知识库容量", number(plan.get("knowledgeCapacityMb")), "MB"));
        result.add(benefit("文件上传数量", number(plan.get("fileUploadLimit")), "个"));
        result.add(benefit("单文件最大", number(plan.get("singleFileSizeMb")), "MB"));
        return Map.of("items", result);
    }

    private Map<String, Object> missingPlanSubscription(User user) {
        String level = canonicalTier(user.getTier());
        return Map.of("level", level.isBlank() ? user.getTier() : level, "status", "ACTIVE".equalsIgnoreCase(user.getStatus()) ? "ACTIVE" : "SUSPENDED",
                "planMissing", true, "quota", Map.of(), "usage", Map.of(), "remaining", Map.of(), "benefits", Map.of("items", List.of()));
    }
    private UsagePeriod currentPeriod() {
        ZonedDateTime now = ZonedDateTime.now(BUSINESS_ZONE);
        ZonedDateTime starts = now.withDayOfMonth(1).toLocalDate().atStartOfDay(BUSINESS_ZONE);
        return new UsagePeriod(starts.toInstant(), starts.plusMonths(1).toInstant());
    }
    private String quotaKeyForBusinessType(String businessType) {
        String value = businessType == null ? "chat" : businessType.trim().toLowerCase(Locale.ROOT);
        return switch (value) { case "topology_generation" -> "topologyGeneration"; case "knowledge_qa", "rag_qa", "knowledge_query" -> "knowledgeQa"; case "network_plan", "network_plan_generation" -> "networkPlan"; default -> "aiChat"; };
    }
    private String quotaErrorCode(String key) {
        return switch (key) {
            case "topologyGeneration" -> "TOPOLOGY_QUOTA_EXCEEDED";
            case "knowledgeQa" -> "KNOWLEDGE_AI_QUOTA_EXCEEDED";
            case "networkPlan" -> "NETWORK_PLAN_QUOTA_EXCEEDED";
            default -> "AI_CALL_QUOTA_EXCEEDED";
        };
    }
    private Map<String, Object> quota(String label, long total) { return Map.of("label", label, "total", Math.max(0, total)); }
    private Map<String, Object> remaining(Map<String, Object> quota, long used) { return Map.of("value", Math.max(0, number(quota.get("total")) - used)); }
    private Map<String, Object> benefit(String label, long value, String unit) { return Map.of("label", label, "value", value, "unit", unit); }
    private String string(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private long number(Object value) { return value instanceof Number number ? Math.max(0, number.longValue()) : parseLong(value); }
    private long parseLong(Object value) { try { return Math.max(0, Long.parseLong(string(value))); } catch (NumberFormatException ignored) { return 0; } }
    @SuppressWarnings("unchecked") private Map<String, Object> map(Object value) { return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of(); }

    private record UsagePeriod(Instant startsAt, Instant endsAt) { }
    public record QuotaCheck(long callsTotal, long callsUsed, long tokensTotal, long tokensUsed, Object nextResetAt) { }

    private record Usage(long aiChat, long topologyGeneration, long knowledgeQa, long networkPlan, long tokens) {
        static Usage from(List<AiUsageLog> logs) {
            long chat = 0, topology = 0, knowledge = 0, networkPlan = 0, tokenCount = 0;
            for (AiUsageLog log : logs) {
                if (!"SUCCESS".equalsIgnoreCase(log.getStatus())) continue;
                tokenCount += log.getTotalTokens();
                String type = log.getBusinessType() == null ? "" : log.getBusinessType().trim().toLowerCase(Locale.ROOT);
                switch (type) {
                    case "topology_generation" -> topology++;
                    case "knowledge_qa", "rag_qa", "knowledge_query" -> knowledge++;
                    case "network_plan", "network_plan_generation" -> networkPlan++;
                    default -> chat++;
                }
            }
            return new Usage(chat, topology, knowledge, networkPlan, tokenCount);
        }
        Map<String, Object> toMap() {
            return Map.of("aiChat", Map.of("used", aiChat), "topologyGeneration", Map.of("used", topologyGeneration),
                    "knowledgeQa", Map.of("used", knowledgeQa), "networkPlan", Map.of("used", networkPlan), "tokens", Map.of("used", tokens));
        }
    }
}
