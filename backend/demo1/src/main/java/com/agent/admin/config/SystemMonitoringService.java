package com.agent.admin.config;

import com.agent.ai.AiModel;
import com.agent.ai.AiModelRepository;
import com.agent.common.audit.AuditLogRepository;
import com.agent.common.audit.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SystemMonitoringService {
    private static final Duration SNAPSHOT_TTL = Duration.ofSeconds(1);
    private static final Duration PERSIST_TTL = Duration.ofSeconds(30);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final DataSource dataSource;
    private final AuditLogRepository audits;
    private final AuditService auditService;
    private final AiModelRepository models;
    private final ConfigurableApplicationContext applicationContext;
    private final SystemServiceHealthRepository healthRecords;
    private final Path uploadDirectory;
    private final Object snapshotLock = new Object();
    private final Map<String, Instant> persistedAt = new ConcurrentHashMap<>();
    private final Map<String, String> persistedStatus = new ConcurrentHashMap<>();
    private volatile Map<String, Object> snapshot = Map.of();
    private volatile Instant snapshotAt = Instant.EPOCH;

    public SystemMonitoringService(DataSource dataSource, AuditLogRepository audits, AuditService auditService,
                                   AiModelRepository models, ConfigurableApplicationContext applicationContext,
                                   SystemServiceHealthRepository healthRecords,
                                   @Value("${topo.upload-dir:uploads}") String uploadDirectory) {
        this.dataSource = dataSource;
        this.audits = audits;
        this.auditService = auditService;
        this.models = models;
        this.applicationContext = applicationContext;
        this.healthRecords = healthRecords;
        this.uploadDirectory = Paths.get(uploadDirectory).toAbsolutePath().normalize();
    }

    public Map<String, Object> snapshot(boolean force) {
        Instant now = Instant.now();
        if (!force && fresh(now)) return snapshot;

        synchronized (snapshotLock) {
            now = Instant.now();
            if (force || !fresh(now)) {
                snapshot = collect(now);
                snapshotAt = now;
            }
            return snapshot;
        }
    }

    private boolean fresh(Instant now) {
        return !snapshot.isEmpty() && Duration.between(snapshotAt, now).compareTo(SNAPSHOT_TTL) < 0;
    }

    private Map<String, Object> collect(Instant checkedAt) {
        double processCpu = processCpuPercent();
        double processMemory = processMemoryPercent();
        List<HealthResult> checks = new ArrayList<>();
        checks.add(probeApi(checkedAt, processCpu, processMemory));
        checks.add(probeMysql(checkedAt));
        checks.add(probeAi(checkedAt));
        checks.add(probeInternalModule("topology-service", "拓扑引擎", "topologyService", checkedAt));
        checks.add(probeFileStorage(checkedAt));

        checks.forEach(this::persistLatestResult);
        long todayLogs = countTodayLogs();
        long todayErrors = countTodayErrors();
        long activeServices = checks.stream().filter(check -> "UP".equals(check.status())).count();
        boolean requiresAttention = checks.stream().anyMatch(check -> "DOWN".equals(check.status())
                || "DEGRADED".equals(check.status()) || "NOT_CONFIGURED".equals(check.status()));

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("requestCount", todayLogs);
        metrics.put("todayLogCount", todayLogs);
        metrics.put("errorCount", todayErrors);
        metrics.put("activeServices", activeServices);
        metrics.put("totalServices", checks.size());
        metrics.put("processCpu", processCpu);
        metrics.put("processMemory", processMemory);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", requiresAttention ? "DEGRADED" : "UP");
        result.put("checkedAt", checkedAt);
        result.put("metrics", metrics);
        result.put("services", checks.stream().map(HealthResult::toMap).toList());
        return result;
    }

    private HealthResult probeApi(Instant checkedAt, double processCpu, double processMemory) {
        long startedAt = System.nanoTime();
        boolean running = applicationContext.isRunning();
        long elapsed = elapsedMs(startedAt);
        return running
                ? new HealthResult("api-service", "平台API", "UP", "应用进程正在响应健康检查", elapsed, null, processCpu, processMemory, checkedAt)
                : new HealthResult("api-service", "平台API", "DOWN", "应用上下文未运行", elapsed, null, processCpu, processMemory, checkedAt);
    }

    private HealthResult probeMysql(Instant checkedAt) {
        long startedAt = System.nanoTime();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(2);
            statement.execute("SELECT 1");
            return new HealthResult("mysql", "MySQL", "UP", "数据库连接与查询正常", elapsedMs(startedAt), null, null, null, checkedAt);
        } catch (Exception exception) {
            return new HealthResult("mysql", "MySQL", "DOWN", conciseError(exception), elapsedMs(startedAt), null, null, null, checkedAt);
        }
    }

    private HealthResult probeAi(Instant checkedAt) {
        long startedAt = System.nanoTime();
        try {
            AiModel model = models.findByEnabledTrueAndStatusAndDeletedAtIsNullOrderByPriorityAscUpdatedAtDesc("ACTIVE").stream().findFirst().orElse(null);
            if (model == null) {
                return new HealthResult("ai-service", "AI模型服务", "NOT_CONFIGURED", "未配置启用中的 AI 模型通道", null, null, null, null, checkedAt);
            }
            URI endpoint = URI.create(model.getBaseUrl());
            String host = endpoint.getHost();
            if (host == null || host.isBlank()) {
                return new HealthResult("ai-service", "AI模型服务", "DEGRADED", "模型服务地址无效", null, null, null, null, checkedAt);
            }
            int port = endpoint.getPort() > 0 ? endpoint.getPort() : "https".equalsIgnoreCase(endpoint.getScheme()) ? 443 : 80;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 2_000);
            }
            return new HealthResult("ai-service", "AI模型服务", "UP", "模型通道网络连通（未发起推理请求）", elapsedMs(startedAt), null, null, null, checkedAt);
        } catch (Exception exception) {
            return new HealthResult("ai-service", "AI模型服务", "DOWN", conciseError(exception), elapsedMs(startedAt), null, null, null, checkedAt);
        }
    }

    private HealthResult probeInternalModule(String key, String label, String beanName, Instant checkedAt) {
        long startedAt = System.nanoTime();
        boolean ready = applicationContext.containsBean(beanName);
        return ready
                ? new HealthResult(key, label, "UP", "内置模块已加载", elapsedMs(startedAt), null, null, null, checkedAt)
                : new HealthResult(key, label, "DOWN", "内置模块未加载", elapsedMs(startedAt), null, null, null, checkedAt);
    }

    private HealthResult probeFileStorage(Instant checkedAt) {
        long startedAt = System.nanoTime();
        try {
            Files.createDirectories(uploadDirectory);
            if (!Files.isWritable(uploadDirectory)) throw new IOException("上传目录不可写");
            return new HealthResult("file-storage", "文件存储", "UP", "上传目录可写", elapsedMs(startedAt), null, null, null, checkedAt);
        } catch (Exception exception) {
            return new HealthResult("file-storage", "文件存储", "DOWN", conciseError(exception), elapsedMs(startedAt), null, null, null, checkedAt);
        }
    }

    private void persistLatestResult(HealthResult current) {
        try {
            String previousStatus = persistedStatus.get(current.key());
            Instant lastPersistedAt = persistedAt.get(current.key());
            SystemServiceHealth existing = null;
            if (previousStatus == null || lastPersistedAt == null) {
                existing = healthRecords.findByServiceKey(current.key()).orElse(null);
                if (existing != null) {
                    previousStatus = existing.getStatus();
                    lastPersistedAt = existing.getCheckedAt();
                    persistedStatus.put(current.key(), previousStatus);
                    persistedAt.put(current.key(), lastPersistedAt);
                }
            }

            boolean statusChanged = previousStatus == null || !previousStatus.equals(current.status());
            boolean due = lastPersistedAt == null || Duration.between(lastPersistedAt, current.checkedAt()).compareTo(PERSIST_TTL) >= 0;
            if (!statusChanged && !due) return;

            if (existing == null) existing = healthRecords.findByServiceKey(current.key()).orElse(null);
            if (existing == null) {
                healthRecords.save(new SystemServiceHealth(current.key(), current.label(), current.status(), current.detail(),
                        current.responseTime(), current.errorRate(), current.cpu(), current.memory(), current.checkedAt()));
            } else {
                existing.update(current.label(), current.status(), current.detail(), current.responseTime(), current.errorRate(),
                        current.cpu(), current.memory(), current.checkedAt());
                healthRecords.save(existing);
            }
            persistedStatus.put(current.key(), current.status());
            persistedAt.put(current.key(), current.checkedAt());
            if (statusChanged) {
                auditService.record("system.monitoring", "HEALTH_CHECK", null,
                        "UP".equals(current.status()) ? "SUCCESS" : "ERROR", current.label() + "：" + current.detail());
            }
        } catch (Exception ignored) {
            // Health reporting must keep working even while its persistence dependency is unavailable.
        }
    }

    private long countTodayLogs() {
        try { return audits.countByCreatedAtGreaterThanEqual(startOfToday()); }
        catch (Exception ignored) { return 0; }
    }

    private long countTodayErrors() {
        try { return audits.countByCreatedAtGreaterThanEqualAndResultNot(startOfToday(), "SUCCESS"); }
        catch (Exception ignored) { return 0; }
    }

    private Instant startOfToday() {
        return LocalDate.now(BUSINESS_ZONE).atStartOfDay(BUSINESS_ZONE).toInstant();
    }

    private long elapsedMs(long startedAt) {
        return Math.max(1, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private double processCpuPercent() {
        if (ManagementFactoryHolder.OS instanceof com.sun.management.OperatingSystemMXBean bean) {
            double load = bean.getProcessCpuLoad();
            if (load >= 0) return roundPercent(load * 100);
        }
        return 0;
    }

    private double processMemoryPercent() {
        Runtime runtime = Runtime.getRuntime();
        return roundPercent((runtime.totalMemory() - runtime.freeMemory()) * 100.0 / Math.max(1, runtime.maxMemory()));
    }

    private double roundPercent(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String conciseError(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message.substring(0, Math.min(180, message.length()));
    }

    private record HealthResult(String key, String label, String status, String detail, Long responseTime,
                                Double errorRate, Double cpu, Double memory, Instant checkedAt) {
        Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", key);
            result.put("label", label);
            result.put("status", status);
            result.put("detail", detail);
            result.put("responseTime", responseTime);
            result.put("errorRate", errorRate);
            result.put("cpu", cpu);
            result.put("memory", memory);
            result.put("checkedAt", checkedAt);
            return result;
        }
    }

    private static final class ManagementFactoryHolder {
        private static final java.lang.management.OperatingSystemMXBean OS = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
    }
}
