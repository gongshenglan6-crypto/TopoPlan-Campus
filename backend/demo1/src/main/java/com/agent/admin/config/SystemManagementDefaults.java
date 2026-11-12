package com.agent.admin.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SystemManagementDefaults {
    private SystemManagementDefaults() {
    }

    static Map<String, Object> rules() {
        return Map.of("ruleGroups", List.of(
                group("ai-service", "AI服务规则", List.of(
                        rule("ai-fallback-model", "AI连续失败自动切换模型", "count", 3),
                        rule("ai-token-warning", "Token使用达到80%预警", "percentage", 80),
                        rule("ai-timeout-retry", "AI请求超时自动重试", "duration", 60)
                )),
                group("topology-service", "拓扑服务规则", List.of(
                        rule("topology-retry", "拓扑生成失败自动重试", "retry", 2),
                        rule("topology-timeout", "拓扑生成超时预警", "duration", 180),
                        rule("topology-node-limit", "拓扑任务节点数量限制", "count", 300)
                )),
                group("knowledge-base", "知识库规则", List.of(
                        rule("knowledge-parse-retry", "RAG解析失败自动重试", "retry", 3),
                        rule("knowledge-vector-warning", "向量化失败预警", "count", 5),
                        rule("knowledge-rag-rate", "RAG检索失败率预警", "percentage", 5)
                )),
                group("security", "安全规则", List.of(
                        rule("security-ip-rate", "单IP高频请求自动限流", "frequency", 120),
                        rule("security-login-failure", "连续登录失败触发风控", "count", 5),
                        rule("security-export-limit", "敏感数据导出频率限制", "frequency", 10)
                )),
                group("system-resource", "系统资源规则", List.of(
                        rule("system-cpu-warning", "CPU使用率达到阈值预警", "percentage", 85),
                        rule("system-memory-warning", "内存使用率达到阈值预警", "percentage", 80),
                        rule("system-health-timeout", "服务健康检查超时告警", "duration", 10)
                ))
        ));
    }

    private static Map<String, Object> group(String id, String label, List<Map<String, Object>> rules) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("id", id);
        group.put("label", label);
        group.put("rules", rules);
        return group;
    }

    private static Map<String, Object> rule(String id, String label, String thresholdType, int thresholdValue) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("id", id);
        rule.put("label", label);
        rule.put("enabled", true);
        rule.put("threshold", Map.of("type", thresholdType, "value", thresholdValue));
        return rule;
    }
}
