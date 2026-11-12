package com.agent.admin.security;

import com.agent.admin.config.AdminConfigService;
import com.agent.admin.config.SystemMonitoringService;
import com.agent.ai.AiUsageLog;
import com.agent.ai.AiUsageLogRepository;
import com.agent.common.api.ApiException;
import com.agent.common.audit.AuditLog;
import com.agent.common.audit.AuditLogRepository;
import com.agent.login.LoginRecord;
import com.agent.login.LoginRecordRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SecurityRiskService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final AdminConfigService configs;
    private final AuditLogRepository audits;
    private final LoginRecordRepository logins;
    private final AiUsageLogRepository aiUsage;
    private final RiskRequestCounterRepository requestCounters;
    private final SystemMonitoringService monitoring;

    public SecurityRiskService(AdminConfigService configs, AuditLogRepository audits, LoginRecordRepository logins,
                               AiUsageLogRepository aiUsage, RiskRequestCounterRepository requestCounters,
                               SystemMonitoringService monitoring) {
        this.configs = configs;
        this.audits = audits;
        this.logins = logins;
        this.aiUsage = aiUsage;
        this.requestCounters = requestCounters;
        this.monitoring = monitoring;
    }

    @Transactional
    public SecurityOverviewResponse overview() {
        Instant now = Instant.now();
        Instant start = LocalDate.now(BUSINESS_ZONE).atStartOfDay(BUSINESS_ZONE).toInstant();
        List<AuditLog> todayAudits = audits.findAll((root, query, builder) ->
                        builder.greaterThanOrEqualTo(root.get("createdAt"), start),
                PageRequest.of(0, 500, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
        List<LoginRecord> loginRecords = logins.findAll().stream()
                .filter(item -> item.getCreatedAt() != null && !item.getCreatedAt().isBefore(start)).toList();
        List<AiUsageLog> aiRecords = aiUsage.findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(start);
        List<SecurityEvent> events = buildEvents(todayAudits, loginRecords, aiRecords);
        long riskCount = todayAudits.stream().filter(this::isRiskAudit).count()
                + loginRecords.stream().filter(item -> "失败".equals(item.getResult())).count()
                + aiRecords.stream().filter(item -> !"SUCCESS".equalsIgnoreCase(item.getStatus())).count();
        long blocked = todayAudits.stream().filter(this::isBlocked).count();
        long activeBlacklist = blacklist().stream().filter(SecurityRiskService::isActiveBlacklist).count();
        long pending = events.stream().filter(item -> "pending".equals(item.status())).count();
        long todayRequests = requestCounters.sumRequestCountByBucketStartGreaterThanEqual(start);
        List<SecurityMetric> metrics = List.of(
                new SecurityMetric("今日请求", todayRequests, "已进入风控检查的 API 请求", "neutral"),
                new SecurityMetric("风险请求", riskCount, "来自登录、审计与 AI 调用记录", "warning"),
                new SecurityMetric("已拦截", blocked, "安全策略已阻止的请求", "success"),
                new SecurityMetric("黑名单", activeBlacklist, "当前生效对象", "danger"),
                new SecurityMetric("待处理事件", pending, "需要管理员复核", "warning")
        );
        return new SecurityOverviewResponse(metrics, events, monitoringStatus(), now);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> blacklist() {
        return configs.list("risk.blacklist").stream().map(this::displayBlacklist).toList();
    }

    @Transactional
    public Map<String, Object> riskPolicy() {
        Map<String, Object> stored = configs.get("risk.policy", "default");
        if (!Boolean.TRUE.equals(stored.get("configured"))) {
            return configs.save("risk.policy", "default", RiskPolicyDefaults.rules());
        }
        Object rawRules = stored.get("rules");
        if (!(rawRules instanceof List<?> existingRules)) return configs.save("risk.policy", "default", RiskPolicyDefaults.rules());
        Map<String, Object> defaults = RiskPolicyDefaults.rules();
        List<Map<String, Object>> merged = new ArrayList<>();
        Set<String> ids = new java.util.HashSet<>();
        for (Object raw : existingRules) {
            if (raw instanceof Map<?, ?> item) {
                Map<String, Object> copy = new LinkedHashMap<>();
                item.forEach((key, value) -> copy.put(String.valueOf(key), value));
                merged.add(copy);
                ids.add(String.valueOf(copy.getOrDefault("id", "")));
            }
        }
        boolean changed = false;
        if (defaults.get("rules") instanceof List<?> defaultRules) {
            for (Object raw : defaultRules) {
                if (raw instanceof Map<?, ?> item) {
                    Object defaultRuleId = item.get("id");
                    if (!ids.add(String.valueOf(defaultRuleId == null ? "" : defaultRuleId))) continue;
                    Map<String, Object> copy = new LinkedHashMap<>();
                    item.forEach((key, value) -> copy.put(String.valueOf(key), value));
                    merged.add(copy);
                    changed = true;
                }
            }
        }
        if (!changed) return stored;
        Map<String, Object> result = new LinkedHashMap<>(stored);
        result.put("rules", merged);
        return configs.save("risk.policy", "default", result);
    }

    @Transactional
    public Map<String, Object> saveRiskPolicy(Map<String, Object> value) {
        return configs.save("risk.policy", "default", value);
    }

    @Transactional
    public Map<String, Object> createBlacklist(Map<String, Object> value) {
        String key = UUID.randomUUID().toString();
        return displayBlacklist(configs.save("risk.blacklist", key, prepareBlacklist(value, null)));
    }

    @Transactional
    public Map<String, Object> updateBlacklist(String id, Map<String, Object> value) {
        Map<String, Object> previous = configs.get("risk.blacklist", id);
        if (!Boolean.TRUE.equals(previous.get("configured"))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RISK_BLACKLIST_NOT_FOUND", "黑名单记录不存在");
        }
        return displayBlacklist(configs.save("risk.blacklist", id, prepareBlacklist(value, previous)));
    }

    @Transactional
    public void deleteBlacklist(String id) {
        configs.delete("risk.blacklist", id);
    }

    @Transactional
    public Map<String, Object> updateEventStatus(String eventId, String status) {
        String normalized = Set.of("pending", "processed", "ignored", "blocked").contains(status) ? status : "pending";
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("status", normalized);
        value.put("eventId", eventId);
        return configs.save("risk.events", eventId, value);
    }

    public static boolean isActiveBlacklist(Map<String, Object> entry) {
        if (entry == null || "expired".equalsIgnoreCase(String.valueOf(entry.getOrDefault("status", "active")))) return false;
        Instant expiresAt = asInstant(entry.get("expiresAt"));
        return expiresAt == null || expiresAt.isAfter(Instant.now());
    }

    private Map<String, Object> prepareBlacklist(Map<String, Object> source, Map<String, Object> previous) {
        Map<String, Object> input = source == null ? Map.of() : source;
        String type = normalizeType(input.getOrDefault("type", previous == null ? "" : previous.get("type")));
        if (!Set.of("user", "ip", "keyword", "api-key").contains(type)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "黑名单类型不合法");
        }
        String rawObject = text(input.containsKey("object") ? input.get("object") : input.get("value"));
        if (rawObject.isBlank() && previous != null) rawObject = text(previous.get("object"));
        if (rawObject.isBlank()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "请填写黑名单对象");

        String duration = normalizeDuration(input.getOrDefault("duration", previous == null ? "permanent" : previous.get("duration")));
        Instant now = Instant.now();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);
        result.put("reason", text(input.getOrDefault("reason", previous == null ? "管理员手动处置" : previous.get("reason"))));
        result.put("source", text(input.getOrDefault("source", previous == null ? "管理员添加" : previous.getOrDefault("source", "管理员添加"))));
        result.put("note", text(input.getOrDefault("note", previous == null ? "" : previous.getOrDefault("note", ""))));
        result.put("duration", duration);
        result.put("status", "active");
        result.put("createdAt", previous == null ? now : previous.getOrDefault("createdAt", now));
        Instant expiresAt = expiresAt(duration, now);
        if (expiresAt != null) result.put("expiresAt", expiresAt);

        if ("api-key".equals(type)) {
            String previousHash = previous == null ? "" : text(previous.get("matcherHash"));
            boolean objectUnchanged = previous != null && rawObject.equals(text(previous.get("object")));
            result.put("object", objectUnchanged ? rawObject : maskApiKey(rawObject));
            result.put("matcherHash", objectUnchanged && !previousHash.isBlank() ? previousHash : sha256(rawObject));
        } else {
            result.put("object", rawObject);
        }
        return result;
    }

    private Map<String, Object> displayBlacklist(Map<String, Object> entry) {
        Map<String, Object> result = new LinkedHashMap<>(entry);
        result.put("status", isActiveBlacklist(entry) ? "active" : "expired");
        result.putIfAbsent("object", text(entry.get("value")));
        result.put("value", result.get("object"));
        return result;
    }

    private List<SecurityEvent> buildEvents(List<AuditLog> auditRecords, List<LoginRecord> loginRecords, List<AiUsageLog> aiRecords) {
        Map<String, String> statuses = configs.list("risk.events").stream().collect(Collectors.toMap(
                item -> String.valueOf(item.getOrDefault("eventId", item.getOrDefault("key", ""))),
                item -> String.valueOf(item.getOrDefault("status", "pending")), (left, right) -> right));
        List<SecurityEvent> result = new ArrayList<>();
        for (LoginRecord login : loginRecords) {
            if (!"失败".equals(login.getResult())) continue;
            String id = "login-" + login.getId();
            String userId = login.getUserId() == null ? login.getLoginIdentifier() : String.valueOf(login.getUserId());
            String rule = "已拦截".equals(login.getRiskStatus()) ? "登录失败防护" : "登录失败审计";
            result.add(new SecurityEvent(id, login.getCreatedAt(), "high", "login",
                    userId + " / " + maskIp(login.getIpAddress()), userId, maskIp(login.getIpAddress()),
                    login.getDevice() + " / " + login.getBrowser(),
                    safe(login.getFailureReason(), "登录失败，已记录安全审计"), rule, "login-" + login.getId(),
                    statuses.getOrDefault(id, "pending"), false));
        }
        for (AuditLog audit : auditRecords) {
            if (!isRiskAudit(audit)) continue;
            String id = "audit-" + audit.getId();
            String subject = audit.getActor() + (audit.getTargetId() == null ? "" : " / " + audit.getTargetId());
            result.add(new SecurityEvent(id, audit.getCreatedAt(), auditLevel(audit), eventType(audit), subject, audit.getActor(), "—", "—",
                    safe(audit.getDetail(), audit.getAction()), audit.getAction(), audit.getTraceId(),
                    statuses.getOrDefault(id, "pending"), false));
        }
        for (AiUsageLog usage : aiRecords) {
            if ("SUCCESS".equalsIgnoreCase(usage.getStatus())) continue;
            String id = "ai-" + usage.getId();
            String userId = String.valueOf(usage.getUserId());
            result.add(new SecurityEvent(id, usage.getCreatedAt(), "high", "ai", userId, userId, "—", "—",
                    safe(usage.getErrorCode(), "AI 调用失败"), usage.getBusinessType(), usage.getRequestId(),
                    statuses.getOrDefault(id, "pending"), true));
        }
        return result.stream().sorted(Comparator.comparing(SecurityEvent::timestamp).reversed()).limit(100).toList();
    }

    private String monitoringStatus() {
        try {
            Map<String, Object> snapshot = monitoring.snapshot(false);
            return "UP".equals(snapshot.get("status")) ? "正常" : "需关注";
        } catch (Exception ignored) {
            return "检测失败";
        }
    }

    private boolean isRiskAudit(AuditLog audit) {
        String result = String.valueOf(audit.getResult()).toUpperCase(Locale.ROOT);
        String level = String.valueOf(audit.getLevel()).toUpperCase(Locale.ROOT);
        return !"SUCCESS".equals(result) || Set.of("ERROR", "WARNING", "SECURITY").contains(level)
                || "RISK_BLOCK".equalsIgnoreCase(audit.getAction());
    }

    private boolean isBlocked(AuditLog audit) {
        return Set.of("BLOCKED", "已拦截", "RISK_BLOCK").contains(audit.getResult())
                || "RISK_BLOCK".equalsIgnoreCase(audit.getAction());
    }

    private String auditLevel(AuditLog audit) {
        String level = String.valueOf(audit.getLevel()).toUpperCase(Locale.ROOT);
        return switch (level) { case "SECURITY", "ERROR" -> "high"; case "WARNING", "WARN" -> "medium"; default -> "low"; };
    }

    private String eventType(AuditLog audit) {
        String module = String.valueOf(audit.getModule()).toLowerCase(Locale.ROOT);
        String action = String.valueOf(audit.getAction()).toLowerCase(Locale.ROOT);
        if (action.contains("risk") || "blocked".equalsIgnoreCase(audit.getResult())) return "block";
        if (module.contains("auth") || action.contains("login")) return "login";
        if (module.contains("ai") || module.contains("model")) return "ai";
        if (module.contains("api")) return "api";
        return "access";
    }

    private static Instant expiresAt(String duration, Instant now) {
        return switch (duration) { case "1h" -> now.plusSeconds(3_600); case "24h" -> now.plusSeconds(86_400); case "7d" -> now.plusSeconds(604_800); case "30d" -> now.plusSeconds(2_592_000); default -> null; };
    }

    private static String normalizeDuration(Object value) {
        String duration = text(value).toLowerCase(Locale.ROOT);
        return switch (duration) { case "1h", "1小时" -> "1h"; case "24h", "24小时" -> "24h"; case "7d", "7天" -> "7d"; case "30d", "30天" -> "30d"; default -> "permanent"; };
    }

    private static Instant asInstant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value == null) return null;
        try { return Instant.parse(String.valueOf(value)); } catch (Exception ignored) { return null; }
    }

    private static String normalizeType(Object value) {
        String type = text(value).toLowerCase(Locale.ROOT);
        return "user_id".equals(type) ? "user" : type;
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    private String maskIp(String value) {
        if (value == null || value.isBlank()) return "—";
        String[] parts = value.split("\\.");
        return parts.length == 4 ? parts[0] + "." + parts[1] + ".xxx.xxx" : value;
    }

    private String safe(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    private static String maskApiKey(String value) { return value.length() <= 6 ? "****" : value.substring(0, 3) + "****" + value.substring(value.length() - 3); }

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
