package com.agent.admin.security;

import com.agent.admin.config.AdminConfigService;
import com.agent.ai.AiUsageLog;
import com.agent.ai.AiUsageLogRepository;
import com.agent.ai.AiConversationMessageRepository;
import com.agent.common.api.ApiException;
import com.agent.common.audit.AuditService;
import com.agent.login.LoginRecordRepository;
import com.agent.login.LoginRequestMetadata;
import com.agent.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Applies the persisted risk policy at the request, login, and AI conversation boundaries. */
@Service
public class RiskEnforcementService {
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(10);
    private static final Duration MINUTE = Duration.ofMinutes(1);

    private final AdminConfigService configs;
    private final AuditService auditService;
    private final LoginRecordRepository loginRecords;
    private final AiConversationMessageRepository conversationMessages;
    private final AiUsageLogRepository usageLogs;
    private final RiskRequestCounterRepository requestCounters;

    public RiskEnforcementService(AdminConfigService configs, AuditService auditService,
                                  LoginRecordRepository loginRecords,
                                  AiConversationMessageRepository conversationMessages,
                                  AiUsageLogRepository usageLogs,
                                  RiskRequestCounterRepository requestCounters) {
        this.configs = configs;
        this.auditService = auditService;
        this.loginRecords = loginRecords;
        this.conversationMessages = conversationMessages;
        this.usageLogs = usageLogs;
        this.requestCounters = requestCounters;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void assertRequestAllowed(String ipAddress, String apiKey) {
        String ip = safeIp(ipAddress);
        activeBlacklist("ip", ip).ifPresent(entry -> block(null, "IP 黑名单", "已拦截来自受限 IP 的请求"));
        if (apiKey != null && !apiKey.isBlank()) {
            activeApiKeyBlacklist(apiKey).ifPresent(entry -> block(null, "API Key 黑名单", "已拦截受限 API Key 请求"));
        }
        if (enabled("ip-rate-limit", true)) {
            long threshold = threshold("ip-rate-limit", 100);
            if (threshold > 0 && incrementRequestCounter(ip) > threshold) {
                block(null, "单IP请求限流", "单 IP 在一分钟内的请求次数超过策略阈值");
            }
        }
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void assertExportAllowed(String ipAddress) {
        if (!enabled("export-rate-limit", false)) return;
        long threshold = threshold("export-rate-limit", 20);
        if (threshold <= 0) return;
        String scope = "export:" + safeIp(ipAddress);
        long total = incrementCounter(scope, Instant.now().truncatedTo(ChronoUnit.HOURS));
        if (total > threshold) {
            block(null, "高频导出限制", "单 IP 在一小时内的导出次数超过策略阈值");
        }
    }

    @Transactional
    public void recordRequestOutcome(String ipAddress, String requestPath, int status) {
        if (!enabled("api-failure", true) || status < 500) return;
        try {
            String scope = "failure:" + safeIp(ipAddress);
            Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);
            requestCounters.incrementFailureCount(scope, bucket);
            long failures = requestCounters.findByIpAddressAndBucketStart(scope, bucket)
                    .map(RiskRequestCounter::getFailureCount)
                    .orElse(0L);
            if (failures == threshold("api-failure", 5)) {
                auditService.record("security.risk", "API_FAILURE_ALERT", null, "WARNING",
                        "API连续失败保护已触发：同一来源一分钟内的服务端失败达到阈值，路径=" + safePath(requestPath), "WARNING");
            }
        } catch (Exception ignored) {
            // A failed response must never be replaced by a monitoring error.
        }
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void assertLoginAllowed(String loginIdentifier, User user, LoginRequestMetadata metadata) {
        String identifier = normalize(loginIdentifier);
        String ip = safeIp(metadata == null ? null : metadata.ipAddress());
        activeBlacklist("ip", ip).ifPresent(entry -> block(user == null ? null : user.getId(), "IP 黑名单", "已拦截来自受限 IP 的登录请求"));
        if (user != null) {
            activeBlacklist("user", String.valueOf(user.getId()))
                    .ifPresent(entry -> block(user.getId(), "用户黑名单", "已拦截受限用户的登录请求"));
        }
        if (enabled("login-failure", true)) {
            long threshold = threshold("login-failure", 5);
            long failures = loginRecords.countByLoginIdentifierIgnoreCaseAndResultAndRiskStatusNotAndCreatedAtGreaterThanEqual(
                    identifier, "失败", "已拦截", Instant.now().minus(LOGIN_WINDOW));
            if (threshold > 0 && failures >= threshold) {
                auditService.recordRiskBlock(user == null ? null : user.getId(), "登录失败防护：十分钟内登录失败次数达到阈值");
                throw new ApiException(HttpStatus.FORBIDDEN, "RISK_BLOCKED", "登录失败次数过多，请在最后一次密码错误的十分钟后重试；被拦截的重试不会延长等待时间。");
            }
        }
    }

    @Transactional
    public void recordFailedLogin(String loginIdentifier, User user) {
        if (!enabled("login-failure", true)) return;
        String identifier = normalize(loginIdentifier);
        long threshold = threshold("login-failure", 5);
        if (threshold <= 0) return;
        long failures = loginRecords.countByLoginIdentifierIgnoreCaseAndResultAndRiskStatusNotAndCreatedAtGreaterThanEqual(
                identifier, "失败", "已拦截", Instant.now().minus(LOGIN_WINDOW));
        if (failures >= threshold) {
            auditService.recordRiskBlock(user == null ? null : user.getId(), "登录失败防护已触发：连续失败次数达到阈值");
        }
    }

    @Transactional
    public void recordSuccessfulLogin(User user, LoginRequestMetadata metadata) {
        if (user == null || !enabled("unusual-login", true)) return;
        List<com.agent.login.LoginRecord> records = loginRecords.findTop5ByUserIdAndResultOrderByCreatedAtDesc(user.getId(), "成功");
        if (records.size() < 2) return;
        String currentIp = safeIp(metadata == null ? null : metadata.ipAddress());
        boolean knownIp = records.stream().skip(1).anyMatch(record -> currentIp.equals(safeIp(record.getIpAddress())));
        if (!knownIp) {
            auditService.record("security.risk", "UNUSUAL_LOGIN", user.getId(), "WARNING",
                    "异常登录检测：用户从新的 IP 地址登录，已生成脱敏安全事件", "WARNING");
        }
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void assertChatAllowed(User user, String content) {
        activeBlacklist("user", String.valueOf(user.getId()))
                .ifPresent(entry -> block(user.getId(), "用户黑名单", "该用户已被限制使用 AI 服务"));
        if (enabled("sensitive-content", true)) {
            activeKeywordBlacklist(content).ifPresent(entry -> block(user.getId(), "关键词黑名单", "AI 输入命中安全限制规则"));
        }
        if (enabled("ai-frequency", true)) {
            long threshold = threshold("ai-frequency", 60);
            long recentMessages = conversationMessages.countByUserIdAndRoleAndCreatedAtGreaterThanEqual(
                    user.getId(), "user", Instant.now().minus(MINUTE));
            if (threshold > 0 && recentMessages >= threshold) {
                block(user.getId(), "AI高频调用检测", "该用户一分钟内的 AI 请求次数超过策略阈值");
            }
        }
        recordTokenAnomalyIfNeeded(user, content);
    }

    private long incrementRequestCounter(String ipAddress) {
        return incrementCounter(ipAddress, Instant.now().truncatedTo(ChronoUnit.MINUTES));
    }

    private long incrementCounter(String scope, Instant bucket) {
        requestCounters.incrementRequestCount(scope, bucket);
        return requestCounters.findByIpAddressAndBucketStart(scope, bucket)
                .map(RiskRequestCounter::getRequestCount)
                .orElse(0L);
    }

    private void recordTokenAnomalyIfNeeded(User user, String content) {
        if (!enabled("token-anomaly", true)) return;
        List<AiUsageLog> history = usageLogs.findByUserIdAndCreatedAtGreaterThanEqual(user.getId(), Instant.now().minus(Duration.ofDays(1)))
                .stream().filter(log -> "SUCCESS".equalsIgnoreCase(log.getStatus())).toList();
        if (history.size() < 3) return;
        long average = Math.max(1, Math.round(history.stream().mapToLong(AiUsageLog::getTotalTokens).average().orElse(0)));
        long estimatedTokens = Math.max(1, (content == null ? 0 : content.length() + 3) / 4);
        long multiplier = threshold("token-anomaly", 300);
        if (estimatedTokens * 100L > average * multiplier) {
            auditService.record("security.risk", "TOKEN_ANOMALY", user.getId(), "WARNING",
                    "Token异常消耗检测：请求预估 Token 高于该用户近24小时平均值，已生成风险事件", "WARNING");
        }
    }

    private Optional<Map<String, Object>> activeBlacklist(String type, String target) {
        if (target == null || target.isBlank()) return Optional.empty();
        return configs.list("risk.blacklist").stream()
                .filter(SecurityRiskService::isActiveBlacklist)
                .filter(entry -> type.equals(normalizeType(entry.get("type"))))
                .filter(entry -> target.equalsIgnoreCase(blacklistObject(entry)))
                .findFirst();
    }

    private Optional<Map<String, Object>> activeKeywordBlacklist(String content) {
        if (content == null || content.isBlank()) return Optional.empty();
        String lowerContent = content.toLowerCase(Locale.ROOT);
        return configs.list("risk.blacklist").stream()
                .filter(SecurityRiskService::isActiveBlacklist)
                .filter(entry -> "keyword".equals(normalizeType(entry.get("type"))))
                .filter(entry -> {
                    String keyword = blacklistObject(entry).toLowerCase(Locale.ROOT);
                    return !keyword.isBlank() && lowerContent.contains(keyword);
                })
                .findFirst();
    }

    private Optional<Map<String, Object>> activeApiKeyBlacklist(String apiKey) {
        String digest = sha256(apiKey);
        return configs.list("risk.blacklist").stream()
                .filter(SecurityRiskService::isActiveBlacklist)
                .filter(entry -> "api-key".equals(normalizeType(entry.get("type"))))
                .filter(entry -> digest.equals(String.valueOf(entry.getOrDefault("matcherHash", ""))))
                .findFirst();
    }

    private boolean enabled(String ruleId, boolean fallback) {
        return rule(ruleId).map(rule -> {
            Object value = rule.get("enabled");
            return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
        }).orElse(fallback);
    }

    private long threshold(String ruleId, long fallback) {
        return rule(ruleId).map(rule -> parsePositiveLong(rule.get("configValue"), fallback)).orElse(fallback);
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> rule(String id) {
        Map<String, Object> stored = configs.get("risk.policy", "default");
        if (!Boolean.TRUE.equals(stored.get("configured"))) {
            stored = configs.save("risk.policy", "default", RiskPolicyDefaults.rules());
        }
        Object rawRules = stored.get("rules");
        if (!(rawRules instanceof List<?> rules)) return Optional.empty();
        return rules.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item)
                .filter(rule -> id.equals(String.valueOf(rule.get("id")))).findFirst();
    }

    private long parsePositiveLong(Object value, long fallback) {
        try {
            long parsed = Long.parseLong(String.valueOf(value).trim());
            return parsed > 0 ? parsed : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void block(Long userId, String rule, String detail) {
        auditService.recordRiskBlock(userId, rule + "：" + detail);
        throw new ApiException(HttpStatus.FORBIDDEN, "RISK_BLOCKED", "请求已被安全策略拦截");
    }

    private static String blacklistObject(Map<String, Object> entry) {
        Object object = entry.get("object");
        return normalize(object == null ? String.valueOf(entry.getOrDefault("value", "")) : String.valueOf(object));
    }

    private static String normalizeType(Object value) {
        String type = normalize(String.valueOf(value));
        return "user_id".equals(type) ? "user" : type;
    }

    private static String safeIp(String value) {
        String ip = normalize(value);
        return ip.isBlank() || "未知IP".equals(ip) ? "0.0.0.0" : ip;
    }

    private static String safePath(String value) {
        if (value == null || value.isBlank()) return "未知路径";
        return value.length() <= 120 ? value : value.substring(0, 120);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
