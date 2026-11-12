package com.agent.ai;

import com.agent.user.User;
import com.agent.user.UserRepository;
import com.agent.subscription.SubscriptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiUsageService {
    private static final DateTimeFormatter TREND_LABEL = DateTimeFormatter.ofPattern("MM-dd");
    private static final Set<String> RMB_CURRENCIES = Set.of("CNY", "RMB", "CN¥", "元");
    private static final String USD = "USD";

    private final AiUsageLogRepository logs;
    private final AiQuotaService quotas;
    private final UserRepository users;
    /** Finance can update this through TOPO_BILLING_USD_TO_RMB_RATE without changing stored usage logs. */
    @Value("${topo.billing.usd-to-rmb-rate:7.2}")
    private BigDecimal usdToRmbRate;

    public AiUsageService(AiUsageLogRepository logs, AiQuotaService quotas, UserRepository users) {
        this.logs = logs;
        this.quotas = quotas;
        this.users = users;
    }

    @Transactional
    public AiUsageLog record(AiModel model, String requestId, String traceId, Long userId, Long conversationId,
                             String businessType, long inputTokens, long outputTokens, long latencyMs,
                             String status, String errorCode) {
        BigDecimal inputPrice = model.getInputTokenPrice();
        BigDecimal outputPrice = model.getOutputTokenPrice();
        BigDecimal unit = BigDecimal.valueOf(Math.max(1, model.getPriceUnit()));
        BigDecimal inputCost = BigDecimal.valueOf(Math.max(0, inputTokens)).divide(unit, 12, RoundingMode.HALF_UP)
                .multiply(inputPrice).setScale(8, RoundingMode.HALF_UP);
        BigDecimal outputCost = BigDecimal.valueOf(Math.max(0, outputTokens)).divide(unit, 12, RoundingMode.HALF_UP)
                .multiply(outputPrice).setScale(8, RoundingMode.HALF_UP);
        AiUsageLog log = logs.save(new AiUsageLog(
                requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId,
                traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId,
                userId, model.getId(), model.getProviderName(), businessType, conversationId,
                inputTokens, outputTokens, inputPrice, outputPrice, inputCost, outputCost,
                model.getCurrency(), Math.max(0, latencyMs), status, errorCode));
        if ("SUCCESS".equalsIgnoreCase(status)) quotas.recordUsage(userId, inputTokens, outputTokens, log.getTotalCost());
        return log;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview(List<Map<String, Object>> tierConfigs) {
        return overview(tierConfigs, "month");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview(List<Map<String, Object>> tierConfigs, String requestedRange) {
        ZoneId zone = ZoneId.systemDefault();
        Instant generatedAt = Instant.now();
        LocalDate today = generatedAt.atZone(zone).toLocalDate();
        RangeSpec range = RangeSpec.from(requestedRange, today, zone);
        List<AiUsageLog> entries = logs.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                range.from().toInstant(), range.toExclusive().toInstant());
        List<User> accountList = users.findAll();
        Map<String, Object> rangeData = buildRange(range, entries, accountList, tierConfigs, generatedAt, zone);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("range", range.key());
        data.put("rangeLabel", range.label());
        data.put("from", range.from().toLocalDate().toString());
        data.put("to", range.toExclusive().minusNanos(1).toLocalDate().toString());
        data.put("rangeData", rangeData);
        data.put("selected", rangeData);
        data.put("ranges", Map.of(range.label(), rangeData));
        data.put("usageStats", rangeData.get("stats"));
        data.put("usageTrend", Map.of(range.label(), rangeData.get("trend")));
        data.put("featureUsage", rangeData.get("features"));
        data.put("tierUsage", rangeData.get("tiers"));

        Map<String, Object> overview = castMap(rangeData.get("overview"));
        data.put("overview", overview);
        data.put("monthlyCost", rangeData.get("totalCost"));
        data.put("monthlyChangePercent", null);
        data.put("totalTokens", rangeData.get("totalTokens"));
        data.put("callCount", rangeData.get("callCount"));
        data.put("successfulCalls", rangeData.get("successfulCalls"));
        data.put("dailyCosts", dailyCosts(entries, zone));
        data.put("modelBreakdown", modelBreakdown(entries));
        data.put("topUsers", topUsers(entries, accountList));
        data.put("generatedAt", generatedAt);
        data.put("updatedAt", generatedAt);
        return data;
    }

    private Map<String, Object> buildRange(RangeSpec range, List<AiUsageLog> entries,
                                           List<User> accountList, List<Map<String, Object>> tierConfigs,
                                           Instant generatedAt, ZoneId zone) {
        List<AiUsageLog> successful = entries.stream().filter(this::isSuccess).toList();
        Map<String, Object> overview = overviewValues(entries, successful);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("range", range.key());
        result.put("rangeLabel", range.label());
        result.put("stats", stats(overview, range, entries));
        result.put("overview", overview);
        result.put("trend", trend(entries, range.from().toLocalDate(), range.toExclusive().minusNanos(1).toLocalDate(), zone));
        result.put("features", featureUsage(entries));
        result.put("tiers", tierUsage(entries, accountList, tierConfigs));
        result.put("summary", summary(overview, entries, tierConfigs, generatedAt));
        result.put("callCount", entries.size());
        result.put("successfulCalls", successful.size());
        result.put("totalTokens", overview.get("tokenUsage"));
        result.put("totalCost", overview.get("totalCost"));
        return result;
    }

    private Map<String, Object> overviewValues(List<AiUsageLog> entries, List<AiUsageLog> successful) {
        long calls = entries.size();
        long tokens = entries.stream().mapToLong(AiUsageLog::getTotalTokens).sum();
        long topologyRequests = entries.stream().filter(this::isTopology).count();
        long topologyGenerated = successful.stream().filter(this::isTopology).count();
        long activeUsers = successful.stream().map(AiUsageLog::getUserId).filter(Objects::nonNull).distinct().count();
        BigDecimal cost = entries.stream().map(AiUsageLog::getTotalCost).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(8, RoundingMode.HALF_UP);
        Set<String> currencies = entries.stream().map(AiUsageLog::getCurrency)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(java.util.Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String currency = currencies.stream().findFirst().orElse(null);
        List<AiUsageLog> measuredEntries = entries.stream().filter(log -> log.getLatencyMs() > 0).toList();
        Long averageLatency = measuredEntries.isEmpty() ? null : Math.round(measuredEntries.stream().mapToLong(AiUsageLog::getLatencyMs).average().orElse(0));
        Double successRate = calls == 0 ? null : roundPercent(successful.size(), calls);
        long peakConcurrency = peakConcurrency(entries);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("aiCalls", calls);
        values.put("tokenUsage", tokens);
        values.put("topologyGenerated", topologyGenerated);
        values.put("topologyRequests", topologyRequests);
        values.put("activeUsers", activeUsers);
        values.put("totalCost", cost);
        values.put("totalCostRmb", entries.stream().map(this::costInRmb).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(8, RoundingMode.HALF_UP));
        values.put("currency", currency);
        values.put("currencies", currencies);
        values.put("avgResponseTimeMs", averageLatency);
        values.put("successRate", successRate);
        values.put("peakConcurrency", peakConcurrency);
        values.put("successfulCalls", successful.size());
        return values;
    }

    private List<Map<String, Object>> stats(Map<String, Object> overview, RangeSpec range, List<AiUsageLog> entries) {
        long calls = number(overview.get("aiCalls"));
        long tokens = number(overview.get("tokenUsage"));
        long topology = number(overview.get("topologyGenerated"));
        long activeUsers = number(overview.get("activeUsers"));
        BigDecimal cost = decimal(overview.get("totalCostRmb"));
        Set<String> currencies = currencies(overview.get("currencies"));
        String costValue = formatCost(cost);
        String costHint = formatCostHint(entries, currencies);
        Long latency = overview.get("avgResponseTimeMs") instanceof Number value ? value.longValue() : null;
        Double successRate = overview.get("successRate") instanceof Number value ? value.doubleValue() : null;
        long peak = number(overview.get("peakConcurrency"));
        return List.of(
                stat("aiCalls", "AI调用次数", String.valueOf(calls), "范围内全部请求记录", "blue", calls),
                stat("tokenUsage", "Token消耗", compactTokens(tokens), calls == 0 ? "当前周期暂无调用" : tokens + " Token", "violet", tokens),
                stat("topologyGenerated", "拓扑生成", String.valueOf(topology), topology == 0 && number(overview.get("topologyRequests")) == 0 ? "当前周期暂无记录" : "成功生成次数", "green", topology),
                stat("activeUsers", "活跃用户", String.valueOf(activeUsers), activeUsers == 0 ? "当前周期暂无有效操作" : "有实际操作记录", "orange", activeUsers),
                stat("totalCost", "调用成本", costValue, costHint, "slate", cost),
                stat("avgResponseTimeMs", "平均响应时长", formatLatency(latency), latency == null ? "当前周期暂无调用" : "基于真实 API 请求", "blue", latency),
                stat("successRate", "调用成功率", successRate == null ? "--" : String.format(java.util.Locale.ROOT, "%.1f%%", successRate), successRate == null ? "当前周期暂无调用" : "成功请求 / 全部请求", "green", successRate),
                stat("peakConcurrency", "峰值并发", String.valueOf(peak), peak == 0 ? "当前周期暂无请求" : "按同一秒请求峰值", "slate", peak)
        );
    }

    private String formatCost(BigDecimal cost) {
        String amount = cost.stripTrailingZeros().toPlainString();
        return "¥" + amount;
    }

    private String formatCostHint(List<AiUsageLog> entries, Set<String> currencies) {
        if (entries.isEmpty()) return "当前周期暂无调用";
        return currencies.stream().anyMatch(currency -> !RMB_CURRENCIES.contains(currency) && !USD.equals(currency))
                ? "按模型价格实时计算，未识别币种未计入人民币汇总"
                : "按模型价格与汇率实时计算，人民币";
    }

    private BigDecimal costInRmb(AiUsageLog log) {
        BigDecimal cost = log.getTotalCost();
        if (cost == null) return null;
        String currency = log.getCurrency() == null ? "" : log.getCurrency().trim().toUpperCase(java.util.Locale.ROOT);
        if (RMB_CURRENCIES.contains(currency)) return cost;
        if (USD.equals(currency)) return cost.multiply(usdToRmbRate == null ? BigDecimal.ZERO : usdToRmbRate);
        return null;
    }

    private Set<String> currencies(Object value) {
        if (value instanceof Set<?> values) {
            return values.stream().map(String::valueOf).collect(Collectors.toCollection(LinkedHashSet::new));
        }
        if (value instanceof Iterable<?> values) {
            Set<String> result = new LinkedHashSet<>();
            values.forEach(item -> result.add(String.valueOf(item)));
            return result;
        }
        return Set.of();
    }

    private Map<String, Object> stat(String key, String label, String value, String hint, String tone, Object rawValue) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", label);
        item.put("value", value);
        item.put("rawValue", rawValue);
        item.put("delta", "");
        item.put("hint", hint);
        item.put("tone", tone);
        return item;
    }

    private List<Map<String, Object>> trend(List<AiUsageLog> entries, LocalDate from, LocalDate to, ZoneId zone) {
        Map<LocalDate, List<AiUsageLog>> byDay = entries.stream().collect(Collectors.groupingBy(
                log -> log.getCreatedAt().atZone(zone).toLocalDate(), LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
            List<AiUsageLog> day = byDay.getOrDefault(cursor, List.of());
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("label", cursor.format(TREND_LABEL));
            point.put("date", cursor.toString());
            point.put("aiCalls", day.size());
            point.put("topology", day.stream().filter(this::isSuccess).filter(this::isTopology).count());
            result.add(point);
        }
        return result;
    }

    private List<Map<String, Object>> featureUsage(List<AiUsageLog> entries) {
        Map<String, Long> counts = entries.stream().collect(Collectors.groupingBy(this::featureKey, LinkedHashMap::new, Collectors.counting()));
        long total = entries.size();
        return counts.entrySet().stream().map(entry -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", entry.getKey());
            item.put("name", featureName(entry.getKey()));
            item.put("calls", entry.getValue());
            item.put("share", total == 0 ? 0 : roundPercent(entry.getValue(), total));
            return item;
        }).toList();
    }

    private List<Map<String, Object>> tierUsage(List<AiUsageLog> entries, List<User> accountList,
                                                List<Map<String, Object>> tierConfigs) {
        Map<String, Long> userCounts = accountList.stream().collect(Collectors.groupingBy(
                user -> normalizeTier(user.getTier()), LinkedHashMap::new, Collectors.counting()));
        Map<Long, User> byId = accountList.stream().filter(user -> user.getId() != null)
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
        Map<String, Long> callCounts = entries.stream().map(AiUsageLog::getUserId).filter(Objects::nonNull)
                .map(byId::get).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(user -> normalizeTier(user.getTier()), LinkedHashMap::new, Collectors.counting()));
        Map<String, String> names = tierConfigs.stream().collect(Collectors.toMap(this::tierCode, this::tierName, (left, right) -> left, LinkedHashMap::new));
        Set<String> codes = new LinkedHashSet<>();
        tierConfigs.forEach(config -> codes.add(tierCode(config)));
        codes.addAll(userCounts.keySet());
        long totalUsers = accountList.size();
        return codes.stream().filter(code -> !code.isBlank()).map(code -> {
            Map<String, Object> item = new LinkedHashMap<>();
            long usersInTier = userCounts.getOrDefault(code, 0L);
            item.put("code", code);
            item.put("name", names.getOrDefault(code, code));
            item.put("users", usersInTier);
            item.put("calls", callCounts.getOrDefault(code, 0L));
            item.put("share", totalUsers == 0 ? 0 : roundPercent(usersInTier, totalUsers));
            return item;
        }).toList();
    }

    private Map<String, Object> summary(Map<String, Object> overview, List<AiUsageLog> entries,
                                        List<Map<String, Object>> tierConfigs, Instant generatedAt) {
        long topologyAll = number(overview.get("topologyRequests"));
        long topologySuccess = number(overview.get("topologyGenerated"));
        Long avgTokens = entries.isEmpty() ? null : Math.round(number(overview.get("tokenUsage")) / (double) entries.size());
        Long avgLatency = overview.get("avgResponseTimeMs") instanceof Number value ? value.longValue() : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("averageTokensPerCall", avgTokens);
        result.put("topologySuccessRate", topologyAll == 0 ? null : roundPercent(topologySuccess, topologyAll));
        result.put("averageResponseTimeMs", avgLatency);
        result.put("successRate", overview.get("successRate"));
        result.put("activeUserCount", overview.get("activeUsers"));
        result.put("activeTierCount", tierConfigs.stream().filter(tier -> Boolean.FALSE != tier.get("enabled")).count());
        result.put("updatedAt", generatedAt);
        return result;
    }

    private long peakConcurrency(List<AiUsageLog> entries) {
        return entries.stream().collect(Collectors.groupingBy(log -> log.getCreatedAt().toEpochMilli() / 1000L, Collectors.counting()))
                .values().stream().mapToLong(Long::longValue).max().orElse(0);
    }

    private List<Map<String, Object>> dailyCosts(List<AiUsageLog> entries, ZoneId zone) {
        Map<String, BigDecimal> costs = entries.stream().collect(Collectors.groupingBy(
                log -> log.getCreatedAt().atZone(zone).toLocalDate().toString(), LinkedHashMap::new,
                Collectors.reducing(BigDecimal.ZERO, AiUsageLog::getTotalCost, BigDecimal::add)));
        return costs.entrySet().stream().map(entry -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", entry.getKey());
            row.put("value", entry.getValue());
            return row;
        }).toList();
    }

    private List<Map<String, Object>> modelBreakdown(List<AiUsageLog> entries) {
        Map<Long, List<AiUsageLog>> byModel = entries.stream().collect(Collectors.groupingBy(AiUsageLog::getModelId, LinkedHashMap::new, Collectors.toList()));
        return byModel.entrySet().stream().map(entry -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("modelId", entry.getKey());
            row.put("calls", entry.getValue().size());
            row.put("tokens", entry.getValue().stream().mapToLong(AiUsageLog::getTotalTokens).sum());
            row.put("cost", entry.getValue().stream().map(AiUsageLog::getTotalCost).reduce(BigDecimal.ZERO, BigDecimal::add));
            return row;
        }).toList();
    }

    private List<Map<String, Object>> topUsers(List<AiUsageLog> entries, List<User> accountList) {
        Map<Long, User> byId = accountList.stream().filter(user -> user.getId() != null).collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
        Map<Long, List<AiUsageLog>> byUser = entries.stream().collect(Collectors.groupingBy(AiUsageLog::getUserId));
        return byUser.entrySet().stream().map(entry -> {
            Map<String, Object> row = new LinkedHashMap<>();
            User user = byId.get(entry.getKey());
            row.put("userId", entry.getKey());
            row.put("username", user == null ? String.valueOf(entry.getKey()) : user.getUsername());
            row.put("displayName", user == null ? String.valueOf(entry.getKey()) : user.getDisplayName());
            row.put("calls", entry.getValue().size());
            row.put("tokens", entry.getValue().stream().mapToLong(AiUsageLog::getTotalTokens).sum());
            row.put("cost", entry.getValue().stream().map(AiUsageLog::getTotalCost).reduce(BigDecimal.ZERO, BigDecimal::add));
            return row;
        }).sorted(Comparator.comparing(row -> decimal(row.get("cost")), Comparator.reverseOrder())).limit(10).toList();
    }

    private String formatLatency(Long latencyMs) {
        if (latencyMs == null) return "--";
        if (latencyMs < 1000) return latencyMs + "ms";
        return String.format(java.util.Locale.ROOT, "%.2fs", latencyMs / 1000d);
    }

    private String compactTokens(long value) {
        if (value >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.2fM", value / 1_000_000d);
        if (value >= 1_000) return String.format(java.util.Locale.ROOT, "%.1fK", value / 1_000d);
        return String.valueOf(value);
    }

    private double roundPercent(long numerator, long denominator) {
        return Math.round(numerator * 10000.0 / denominator) / 100.0;
    }

    private boolean isSuccess(AiUsageLog log) { return "SUCCESS".equalsIgnoreCase(log.getStatus()); }
    private boolean isTopology(AiUsageLog log) { return "topology_generation".equalsIgnoreCase(log.getBusinessType()); }
    private String featureKey(AiUsageLog log) { return log.getBusinessType() == null || log.getBusinessType().isBlank() ? "unknown" : log.getBusinessType(); }
    private String featureName(String value) {
        return switch (value) {
            case "chat" -> "AI对话";
            case "requirement_analysis" -> "AI需求分析";
            case "topology_generation" -> "AI拓扑生成";
            case "network_plan" -> "网络方案生成";
            case "risk_analysis" -> "风险与优化分析";
            case "document_generation" -> "工程文档生成";
            case "web_search" -> "联网搜索";
            case "embedding" -> "知识库处理";
            default -> value;
        };
    }
    private String normalizeTier(String value) {
        return SubscriptionService.canonicalTier(value);
    }
    private String tierCode(Map<String, Object> config) { return normalizeTier(String.valueOf(config.getOrDefault("code", config.getOrDefault("id", "")))); }
    private String tierName(Map<String, Object> config) { return String.valueOf(config.getOrDefault("name", config.getOrDefault("code", config.getOrDefault("id", "")))); }
    private long number(Object value) { return value instanceof Number number ? number.longValue() : 0; }
    private BigDecimal decimal(Object value) { return value instanceof BigDecimal decimal ? decimal : value instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : BigDecimal.ZERO; }
    @SuppressWarnings("unchecked") private Map<String, Object> castMap(Object value) { return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of(); }

    private record RangeSpec(String key, String label, java.time.ZonedDateTime from, java.time.ZonedDateTime toExclusive) {
        static RangeSpec from(String raw, LocalDate today, ZoneId zone) {
            String key = switch (raw == null ? "month" : raw.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "7d", "7days", "week" -> "7d";
                case "90d", "90days" -> "90d";
                default -> "month";
            };
            LocalDate start = switch (key) {
                case "7d" -> today.minusDays(6);
                case "90d" -> today.minusDays(89);
                default -> today.withDayOfMonth(1);
            };
            String label = switch (key) {
                case "7d" -> "近7天";
                case "90d" -> "近90天";
                default -> "本月";
            };
            return new RangeSpec(key, label, start.atStartOfDay(zone), today.plusDays(1).atStartOfDay(zone));
        }
    }
}
