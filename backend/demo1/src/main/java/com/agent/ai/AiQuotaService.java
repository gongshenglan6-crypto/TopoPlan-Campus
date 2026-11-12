package com.agent.ai;

import com.agent.common.api.ApiException;
import com.agent.subscription.SubscriptionService;
import com.agent.user.User;
import com.agent.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiQuotaService {
    private final AiUserQuotaRepository quotas;
    private final UserRepository users;
    private final SubscriptionService subscriptions;

    public AiQuotaService(AiUserQuotaRepository quotas, UserRepository users, SubscriptionService subscriptions) {
        this.quotas = quotas;
        this.users = users;
        this.subscriptions = subscriptions;
    }

    @Transactional
    public void reserveChat(User user, long estimatedInputTokens) {
        reserve(user, "chat", estimatedInputTokens);
    }

    /** Performs the server-side preflight and persists a short-lived reservation so
     * concurrent requests cannot pass the same remaining allowance. */
    @Transactional
    public void reserve(User user, String businessType, long estimatedInputTokens) {
        String type = normalizeBusinessType(businessType);
        // Lock the user row as well as an existing quota row. This closes the
        // first-request race where two concurrent calls both try to create the
        // reservation record for a user that has never called AI before.
        users.findByIdForUpdate(user.getId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
        AiUserQuota quota = quotas.findByUserIdForUpdate(user.getId()).orElseGet(() -> quotas.save(new AiUserQuota(user.getId(), user.getDailyQuota())));
        SubscriptionService.QuotaCheck check = subscriptions.quotaCheck(user, type, estimatedInputTokens);
        long reservedCalls = quota.reservedCalls(type);
        long reservedTokens = quota.getReservedTokens();
        if (reservedCalls >= check.callsTotal() - check.callsUsed()) {
            throw quotaExceeded(type, "本周期额度已用完");
        }
        // Keep at least one output token available. Without this strict boundary,
        // an input that exactly consumes the remaining allowance would still be
        // sent with max_tokens=1 and exceed the configured token quota.
        if (reservedTokens + Math.max(0, estimatedInputTokens)
                >= check.tokensTotal() - check.tokensUsed()) {
            throw quotaExceeded("tokens", "本周期 Token 额度不足");
        }
        quota.reserve(type, estimatedInputTokens);
        quotas.save(quota);
    }

    @Transactional
    public void refundChat(Long userId) {
        release(userId, "chat", 0);
    }

    @Transactional
    public void release(Long userId, String businessType, long estimatedInputTokens) {
        if (userId == null) return;
        String type = normalizeBusinessType(businessType);
        quotas.findByUserIdForUpdate(userId).ifPresent(quota -> {
            quota.release(type, estimatedInputTokens);
            quotas.save(quota);
        });
    }

    @Transactional
    public void recordUsage(Long userId, long inputTokens, long outputTokens, BigDecimal totalCost) {
        // AiUsageLog is the period usage source of truth. Personal quota rows only retain explicit overrides.
    }

    @Transactional
    public List<Map<String, Object>> listForAdmin() {
        return subscriptions.adminQuotaRows();
    }

    @Transactional
    public Map<String, Object> updateForAdmin(Long userId, Map<String, Object> values) {
        User user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
        AiUserQuota quota = quotaFor(user);
        Integer rpm = number(values == null ? null : values.get("rpm"));
        Long tpm = longNumber(values == null ? null : values.get("tpm"));
        Integer customDailyCalls = number(values == null ? null : values.get("customDailyCalls"));
        quota.applyAdminValues(null, null, rpm, tpm, customDailyCalls,
                values == null ? null : values.get("allowedModels"));
        String tier = text(values == null ? null : values.get("tier"));
        if (tier != null) {
            subscriptions.assignTier(user, tier);
            users.save(user);
        }
        quotas.save(quota);
        return listForAdmin().stream().filter(row -> userId.equals(row.get("userId"))).findFirst().orElseThrow();
    }

    private AiUserQuota quotaFor(User user) { return quotas.findByUserId(user.getId()).orElseGet(() -> quotas.save(new AiUserQuota(user.getId(), user.getDailyQuota()))); }
    private Integer number(Object value) { try { return value == null ? null : Integer.valueOf(String.valueOf(value)); } catch (NumberFormatException ignored) { return null; } }
    private Long longNumber(Object value) { try { return value == null ? null : Long.valueOf(String.valueOf(value)); } catch (NumberFormatException ignored) { return null; } }
    private String text(Object value) { return value == null || String.valueOf(value).trim().isBlank() ? null : String.valueOf(value).trim(); }

    public int maxOutputTokens(User user, String businessType, long estimatedInputTokens, int modelMax) {
        Map<String, Object> subscription = subscriptions.subscriptionFor(user);
        Map<String, Object> quota = map(subscription.get("quota"));
        Map<String, Object> usage = map(subscription.get("usage"));
        long total = number(map(quota.get("tokens")).get("total"));
        long used = number(map(usage.get("tokens")).get("used"));
        if (total <= 0) throw quotaExceeded("tokens", "本周期 Token 额度不足");
        long remaining = total - used - Math.max(0, estimatedInputTokens);
        AiUserQuota reservation = quotas.findByUserId(user.getId()).orElse(null);
        if (reservation != null) remaining = total - used - reservation.getReservedTokens();
        if (remaining <= 0) throw quotaExceeded("tokens", "本周期 Token 额度不足");
        return (int) Math.max(1, Math.min((long) Math.max(1, modelMax), remaining));
    }

    private String normalizeBusinessType(String value) { return value == null || value.isBlank() ? "chat" : value.trim().toLowerCase(java.util.Locale.ROOT); }
    private ApiException quotaExceeded(String type, String message) {
        String code = switch (type) { case "topology_generation" -> "TOPOLOGY_QUOTA_EXCEEDED"; case "knowledge_qa" -> "KNOWLEDGE_AI_QUOTA_EXCEEDED"; case "network_plan" -> "NETWORK_PLAN_QUOTA_EXCEEDED"; case "tokens" -> "TOKEN_QUOTA_EXCEEDED"; default -> "AI_CALL_QUOTA_EXCEEDED"; };
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code, message);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) { return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of(); }
}
