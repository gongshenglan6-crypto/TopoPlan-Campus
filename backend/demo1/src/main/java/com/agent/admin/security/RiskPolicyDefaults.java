package com.agent.admin.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RiskPolicyDefaults {
    private RiskPolicyDefaults() { }

    public static Map<String, Object> rules() {
        return Map.of("rules", List.of(
                rule("login-failure", "high", "登录失败防护", "连续登录失败 5 次后触发限制", "5", "次 / 10分钟", true),
                rule("ip-rate-limit", "medium", "单IP请求限流", "防止单个 IP 短时间大量请求", "100", "次 / 分钟", true),
                rule("ai-frequency", "medium", "AI高频调用检测", "检测用户短时间异常调用 AI 接口", "60", "次 / 分钟", true),
                rule("token-anomaly", "high", "Token异常消耗检测", "Token 使用量短时间异常增长时触发风险", "300", "%", true),
                rule("api-failure", "medium", "API连续失败保护", "接口连续失败达到阈值后触发异常记录", "5", "次", true),
                rule("unusual-login", "medium", "异常登录检测", "检测非常用地区、设备或 IP 登录行为", "", "", true),
                rule("sensitive-content", "high", "敏感内容检测", "检测可能存在风险的 AI 输入内容", "", "", true),
                rule("export-rate-limit", "low", "高频导出限制", "防止短时间大量生成或导出文件", "20", "次 / 小时", false),
                rule("api-burst-protection", "high", "API突发流量保护", "检测短时间内突发式接口调用并自动限流", "30", "次 / 10秒", true),
                rule("prompt-injection", "high", "Prompt注入检测", "识别越权指令、提示注入和异常工具调用请求", "高风险标签", "", true),
                rule("multi-account-ip", "medium", "多账号IP访问检测", "同一 IP 短时间访问多个账号时标记风险", "5", "个账号 / 10分钟", true),
                rule("rag-abuse", "medium", "知识库异常检索", "检测知识库接口高频检索和批量导出行为", "60", "次 / 分钟", false)
        ));
    }

    private static Map<String, Object> rule(String id, String level, String name, String description,
                                             String configValue, String configUnit, boolean enabled) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id); value.put("level", level); value.put("name", name); value.put("description", description);
        value.put("configValue", configValue); value.put("configUnit", configUnit); value.put("enabled", enabled);
        value.put("configLabel", configValue.isBlank() ? "启用" : configValue + " " + configUnit);
        return value;
    }
}
