package com.agent.admin.dashboard;

import com.agent.admin.config.AdminConfigService;
import com.agent.admin.config.SystemMonitoringService;
import com.agent.ai.AiConversationMessage;
import com.agent.ai.AiConversationMessageRepository;
import com.agent.ai.AiUsageLog;
import com.agent.ai.AiUsageLogRepository;
import com.agent.common.audit.AuditLogRepository;
import com.agent.login.LoginRecord;
import com.agent.login.LoginRecordRepository;
import com.agent.project.Project;
import com.agent.project.ProjectRepository;
import com.agent.topology.TopologyDocument;
import com.agent.topology.TopologyDocumentRepository;
import com.agent.topology.TopologyVersion;
import com.agent.topology.TopologyVersionRepository;
import com.agent.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminDashboardService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("MM-dd");

    private final UserRepository users;
    private final ProjectRepository projects;
    private final LoginRecordRepository loginRecords;
    private final AiUsageLogRepository usageLogs;
    private final AiConversationMessageRepository conversationMessages;
    private final TopologyVersionRepository topologyVersions;
    private final TopologyDocumentRepository topologyDocuments;
    private final AuditLogRepository audits;
    private final AdminConfigService configs;
    private final SystemMonitoringService monitoring;
    private final ObjectMapper mapper;

    public AdminDashboardService(UserRepository users, ProjectRepository projects, LoginRecordRepository loginRecords,
                                 AiUsageLogRepository usageLogs, AiConversationMessageRepository conversationMessages,
                                 TopologyVersionRepository topologyVersions, TopologyDocumentRepository topologyDocuments,
                                 AuditLogRepository audits, AdminConfigService configs, SystemMonitoringService monitoring,
                                 ObjectMapper mapper) {
        this.users = users; this.projects = projects; this.loginRecords = loginRecords; this.usageLogs = usageLogs;
        this.conversationMessages = conversationMessages; this.topologyVersions = topologyVersions;
        this.topologyDocuments = topologyDocuments; this.audits = audits; this.configs = configs;
        this.monitoring = monitoring; this.mapper = mapper;
    }

    @Transactional
    public DashboardResponse getOverview() {
        Instant generatedAt = Instant.now();
        LocalDate today = generatedAt.atZone(BUSINESS_ZONE).toLocalDate();
        Instant todayStart = today.atStartOfDay(BUSINESS_ZONE).toInstant();
        List<AiUsageLog> usage = usageLogs.findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(today.minusDays(89).atStartOfDay(BUSINESS_ZONE).toInstant());
        List<LoginRecord> logins = loginRecords.findAll();
        List<TopologyVersion> topology = topologyVersions.findAll();
        List<Project> projectList = projects.findAll();
        List<AiConversationMessage> messages = conversationMessages.findAll();
        Map<String, Object> health = monitoring.snapshot(false);
        Map<String, Object> healthMetrics = asMap(health.get("metrics"));
        String serviceStatus = "UP".equals(health.get("status")) ? "正常" : "需关注";
        long activeUsers = logins.stream().filter(item -> !item.getCreatedAt().isBefore(todayStart))
                .filter(item -> "成功".equalsIgnoreCase(item.getResult())).map(LoginRecord::getUserId)
                .filter(Objects::nonNull).distinct().count();
        long todayTopology = topology.stream().filter(item -> !item.getCreatedAt().isBefore(todayStart)).count();
        long todayAiTopology = usage.stream().filter(item -> !item.getCreatedAt().isBefore(todayStart))
                .filter(item -> "topology_generation".equalsIgnoreCase(item.getBusinessType())).count();
        long todayExports = usage.stream().filter(item -> !item.getCreatedAt().isBefore(todayStart))
                .filter(item -> "document_generation".equalsIgnoreCase(item.getBusinessType())).count();
        List<DashboardMetric> metrics = List.of(
                new DashboardMetric("用户总数", users.count(), "", 0, "已持久化账户", "blue", false),
                new DashboardMetric("今日活跃用户", activeUsers, "", 0, "成功登录用户", "green", true),
                new DashboardMetric("今日拓扑绘制", todayTopology, "", 0, "拓扑版本写入记录", "violet", true),
                new DashboardMetric("AI拓扑生成", todayAiTopology, "", 0, "topology_generation 日志", "cyan", true),
                new DashboardMetric("工程文档导出", todayExports, "", 0, "document_generation 日志", "amber", true)
        );
        return new DashboardResponse(metrics, successRatePoints(usage, today), keywordBars(messages),
                audits.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 8)).stream().map(DashboardEvent::from).toList(),
                trends(usage, today), creationModes(projectList), deviceTypes(), aiCapabilities(usage, todayStart),
                resourceStats(), serviceStatus, generatedAt);
    }

    private List<DashboardPoint> successRatePoints(List<AiUsageLog> source, LocalDate today) {
        List<DashboardPoint> points = new ArrayList<>();
        for (int offset = 6; offset >= 0; offset--) {
            LocalDate day = today.minusDays(offset);
            List<AiUsageLog> entries = source.stream().filter(log -> sameDay(log.getCreatedAt(), day)).toList();
            long success = entries.stream().filter(this::successful).count();
            points.add(new DashboardPoint(day.format(DAY_FORMAT), entries.isEmpty() ? 0 : success * 100.0 / entries.size()));
        }
        return points;
    }

    private Map<Integer, List<DashboardTrendPoint>> trends(List<AiUsageLog> source, LocalDate today) {
        Map<Integer, List<DashboardTrendPoint>> result = new LinkedHashMap<>();
        result.put(7, dailyTrend(source, today, 7)); result.put(30, dailyTrend(source, today, 30)); result.put(90, weeklyTrend(source, today));
        return result;
    }

    private List<DashboardTrendPoint> dailyTrend(List<AiUsageLog> source, LocalDate today, int days) {
        List<DashboardTrendPoint> result = new ArrayList<>();
        for (int offset = days - 1; offset >= 0; offset--) {
            LocalDate day = today.minusDays(offset);
            List<AiUsageLog> entries = source.stream().filter(log -> sameDay(log.getCreatedAt(), day)).toList();
            result.add(new DashboardTrendPoint(day.format(DAY_FORMAT), countBusiness(entries, "topology_generation"), entries.size(), countBusiness(entries, "document_generation")));
        }
        return result;
    }

    private List<DashboardTrendPoint> weeklyTrend(List<AiUsageLog> source, LocalDate today) {
        List<DashboardTrendPoint> result = new ArrayList<>();
        for (int offset = 83; offset >= 0; offset -= 7) {
            LocalDate from = today.minusDays(offset + 6L), to = today.minusDays(offset);
            List<AiUsageLog> entries = source.stream().filter(log -> {
                LocalDate day = log.getCreatedAt().atZone(BUSINESS_ZONE).toLocalDate();
                return !day.isBefore(from) && !day.isAfter(to);
            }).toList();
            result.add(new DashboardTrendPoint(from.format(DAY_FORMAT), countBusiness(entries, "topology_generation"), entries.size(), countBusiness(entries, "document_generation")));
        }
        return result;
    }

    private List<DashboardDistributionItem> creationModes(List<Project> source) {
        Map<String, Long> grouped = source.stream().collect(Collectors.groupingBy(item -> item.getType() == null || item.getType().isBlank() ? "未分类项目" : item.getType(), LinkedHashMap::new, Collectors.counting()));
        return percentageItems(grouped);
    }

    private List<DashboardDistributionItem> deviceTypes() {
        Map<String, Long> grouped = new LinkedHashMap<>();
        for (TopologyDocument document : topologyDocuments.findAll()) {
            try {
                JsonNode nodes = mapper.readTree(document.getNodesJson());
                if (!nodes.isArray()) continue;
                nodes.forEach(node -> { String type = node.path("type").asText("").trim(); if (!type.isBlank()) grouped.merge(deviceLabel(type), 1L, Long::sum); });
            } catch (Exception ignored) { }
        }
        return percentageItems(grouped);
    }

    private List<DashboardCapability> aiCapabilities(List<AiUsageLog> source, Instant todayStart) {
        Map<String, List<AiUsageLog>> grouped = source.stream().filter(log -> !log.getCreatedAt().isBefore(todayStart))
                .collect(Collectors.groupingBy(log -> normalizeBusinessType(log.getBusinessType()), LinkedHashMap::new, Collectors.toList()));
        return grouped.entrySet().stream().map(entry -> {
            List<AiUsageLog> logs = entry.getValue(); long success = logs.stream().filter(this::successful).count();
            long latency = Math.round(logs.stream().mapToLong(AiUsageLog::getLatencyMs).average().orElse(0));
            return new DashboardCapability(entry.getKey(), businessLabel(entry.getKey()), logs.size(), logs.isEmpty() ? 0 : success * 100.0 / logs.size(), latency, success == logs.size() ? "正常" : "需关注");
        }).sorted(Comparator.comparingLong(DashboardCapability::calls).reversed()).toList();
    }

    private DashboardResourceStats resourceStats() {
        List<Map<String, Object>> resources = configs.list("knowledge.devices");
        long enabled = resources.stream().filter(item -> !Boolean.FALSE.equals(item.get("enabled"))).count();
        return new DashboardResourceStats(resources.size(), enabled, resources.size() - enabled);
    }

    private List<DashboardKeyword> keywordBars(List<AiConversationMessage> source) {
        Map<String, Long> terms = new LinkedHashMap<>(); Set<String> candidates = Set.of("VLAN", "AP", "防火墙", "冗余", "核心交换机", "PoE", "机房", "SSID");
        source.forEach(message -> candidates.forEach(term -> { long count = occurrences(message.getContent(), term); if (count > 0) terms.merge(term, count, Long::sum); }));
        return terms.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(8).map(entry -> new DashboardKeyword(entry.getKey(), entry.getValue())).toList();
    }

    private List<DashboardDistributionItem> percentageItems(Map<String, Long> grouped) {
        long total = grouped.values().stream().mapToLong(Long::longValue).sum(); if (total == 0) return List.of();
        return grouped.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).map(entry -> new DashboardDistributionItem(entry.getKey(), entry.getValue() * 100.0 / total, entry.getValue())).toList();
    }

    private long countBusiness(List<AiUsageLog> logs, String type) { return logs.stream().filter(log -> type.equalsIgnoreCase(log.getBusinessType())).count(); }
    private boolean sameDay(Instant value, LocalDate day) { return value != null && value.atZone(BUSINESS_ZONE).toLocalDate().equals(day); }
    private boolean successful(AiUsageLog log) { return "SUCCESS".equalsIgnoreCase(log.getStatus()); }
    private String normalizeBusinessType(String value) { return value == null || value.isBlank() ? "unknown" : value.trim().toLowerCase(Locale.ROOT); }
    private String businessLabel(String value) { return switch (value) { case "chat" -> "AI对话"; case "requirement_analysis" -> "需求分析"; case "topology_generation" -> "拓扑生成"; case "network_plan" -> "方案生成"; case "risk_analysis" -> "风险分析"; case "document_generation" -> "文档生成"; case "web_search" -> "联网搜索"; case "embedding" -> "知识库处理"; default -> value; }; }
    private String deviceLabel(String value) { return switch (value) { case "core-switch", "layer3-switch" -> "核心交换机"; case "access-switch" -> "接入交换机"; case "wireless-ap" -> "无线 AP"; case "router" -> "路由器"; case "firewall" -> "防火墙"; case "server" -> "服务器"; case "database" -> "数据库"; case "pc" -> "PC 终端"; default -> value; }; }
    private long occurrences(String text, String term) { if (text == null || text.isBlank()) return 0; long count = 0; int offset = 0; while ((offset = text.indexOf(term, offset)) >= 0) { count++; offset += term.length(); } return count; }
    @SuppressWarnings("unchecked") private Map<String, Object> asMap(Object value) { return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of(); }
}
