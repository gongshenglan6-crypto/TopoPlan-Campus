package com.agent.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic first-pass orchestration. It protects user facts before any model call. */
@Component
public class AiOrchestrator {
    private static final Pattern USER_COUNT = Pattern.compile("(?<!\\d)(\\d{1,6})\\s*(?:台\\s*)?(?:人|名|用户|终端)");
    private static final Pattern FLOOR_COUNT = Pattern.compile("(?<!\\d)([一二两三四五六七八九十\\d]{1,3})\\s*层");
    private static final Pattern PORT_COUNT = Pattern.compile("(?<!\\d)(\\d{1,5})\\s*口");
    private static final Pattern SPEED = Pattern.compile("(?<!\\d)(\\d{1,3})\\s*[x×*]\\s*(\\d{1,3})\\s*[Gg]");

    private final ObjectMapper mapper;

    public AiOrchestrator(ObjectMapper mapper) { this.mapper = mapper; }

    public Decision resolve(String originalUserMessage, boolean hasProjectContext) {
        String original = originalUserMessage == null ? "" : originalUserMessage.trim();
        String value = original.toLowerCase(Locale.ROOT);
        String intent;
        String promptTaskType;
        if (value.isBlank() || value.matches("(?:你好|您好|嗨|hello|hi|谢谢|感谢|继续|好的|ok|收到)[！!。.?？\\s]*")) {
            intent = "GENERAL_CHAT";
            promptTaskType = "TOPOLOGY_QA";
        } else if (value.matches(".*(生成拓扑|创建拓扑|画拓扑|拓扑图).*")) {
            intent = "TOPOLOGY_GENERATION";
            promptTaskType = "TOPOLOGY_GENERATION";
        } else if (value.matches(".*(修改拓扑|调整拓扑|新增设备|删除设备|补全拓扑|增加.*设备).*")) {
            intent = "TOPOLOGY_MODIFICATION";
            promptTaskType = "TOPOLOGY_OPTIMIZATION";
        } else if (value.matches(".*(规划|设计|方案|架构).*") && hasPlanningFact(original)) {
            intent = "NETWORK_PLANNING";
            promptTaskType = "NETWORK_PLANNING";
        } else if (isSimpleNetworkQuestion(value)) {
            // Keep short protocol/concept questions on the clean answer path.
            intent = "NETWORK_QA";
            promptTaskType = "NETWORK_QA";
        } else if (value.matches(".*(vlan|ip地址|网段|子网|地址规划).*")) {
            intent = "IP_VLAN_PLANNING";
            promptTaskType = "IP_VLAN_PLANNING";
        } else if (value.matches(".*(检查拓扑|拓扑检查|校验拓扑|合理吗|是否合理|结构问题).*") && hasProjectContext) {
            intent = "TOPOLOGY_CHECK";
            promptTaskType = "TOPOLOGY_CHECK";
        } else if (value.matches(".*(风险|隐患|单点|安全风险|容量风险|扩展风险).*")) {
            intent = "RISK_ANALYSIS";
            promptTaskType = hasProjectContext ? "RISK_ANALYSIS" : "RAG_QA";
        } else if (value.matches(".*(不通|ping|丢包|故障|报错|连不上|无法访问).*")) {
            intent = "TROUBLESHOOTING";
            promptTaskType = hasProjectContext ? "TOPOLOGY_QA" : "RAG_QA";
        } else if (value.matches(".*(为什么|为何|作用|原理|nat|vrrp|ospf|防火墙放|核心.*汇聚|接入层).*")) {
            intent = hasProjectContext && value.matches(".*(当前|这里|本项目|这台|这个).*") ? "DESIGN_EXPLANATION" : "NETWORK_QA";
            promptTaskType = "DESIGN_EXPLANATION".equals(intent) ? "DESIGN_EXPLANATION" : "NETWORK_QA";
        } else if (hasProjectContext) {
            intent = "PROJECT_QA";
            promptTaskType = "TOPOLOGY_QA";
        } else {
            intent = "NETWORK_QA";
            promptTaskType = "NETWORK_QA";
        }

        Map<String, Object> requirements = extractRequirements(original);
        boolean planning = isPlanning(intent);
        boolean needsProject = hasProjectContext && ("PROJECT_QA".equals(intent) || "DESIGN_EXPLANATION".equals(intent)
                || "TROUBLESHOOTING".equals(intent) || "TOPOLOGY_CHECK".equals(intent)
                || "RISK_ANALYSIS".equals(intent) || intent.contains("TOPOLOGY"));
        boolean needsRag = shouldUseRag(intent, original);
        boolean prevalidate = planning || "TOPOLOGY_GENERATION".equals(intent) || "IP_VLAN_PLANNING".equals(intent);
        return new Decision(intent, confidence(intent), promptTaskType, original, json(requirements),
                number(requirements.get("userCount")), number(requirements.get("portCount")), needsProject, needsRag, planning, prevalidate);
    }

    /** Honor the explicit task selected by a project workspace. */
    public Decision withTaskType(Decision base, String requestedTaskType, boolean hasProjectContext) {
        if (base == null || requestedTaskType == null) return base;
        String task = requestedTaskType.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("TOPOLOGY_GENERATION", "TOPOLOGY_PLANNING", "TOPOLOGY_OPTIMIZATION",
                "TOPOLOGY_CHECK", "RISK_ANALYSIS", "DESIGN_EXPLANATION", "TOPOLOGY_QA",
                "NETWORK_QA", "NETWORK_PLANNING", "IP_VLAN_PLANNING").contains(task)) return base;
        boolean planning = Set.of("TOPOLOGY_GENERATION", "TOPOLOGY_PLANNING", "TOPOLOGY_OPTIMIZATION", "IP_VLAN_PLANNING").contains(task);
        boolean knowledge = Set.of("TOPOLOGY_GENERATION", "TOPOLOGY_PLANNING", "TOPOLOGY_OPTIMIZATION",
                "TOPOLOGY_CHECK", "RISK_ANALYSIS", "DESIGN_EXPLANATION", "NETWORK_PLANNING", "IP_VLAN_PLANNING").contains(task);
        boolean requiresProject = hasProjectContext && !Set.of("NETWORK_QA", "NETWORK_PLANNING", "IP_VLAN_PLANNING").contains(task);
        String intent = switch (task) {
            case "TOPOLOGY_GENERATION" -> "TOPOLOGY_GENERATION";
            case "TOPOLOGY_PLANNING" -> "NETWORK_PLANNING";
            case "TOPOLOGY_OPTIMIZATION" -> "TOPOLOGY_MODIFICATION";
            case "TOPOLOGY_CHECK" -> "TOPOLOGY_CHECK";
            case "RISK_ANALYSIS" -> "RISK_ANALYSIS";
            case "DESIGN_EXPLANATION" -> "DESIGN_EXPLANATION";
            case "TOPOLOGY_QA" -> "PROJECT_QA";
            default -> task;
        };
        return new Decision(intent, base.confidence(), task, base.originalUserMessage(), base.structuredRequirements(),
                base.userCount(), base.portCount(), requiresProject, knowledge, planning, planning);
    }

    private boolean hasPlanningFact(String value) {
        return value.matches(".*(?:\\d{1,6}\\s*(?:人|名|用户|终端)|园区|企业|公司|局域网|网络).*");
    }

    private boolean shouldUseRag(String intent, String original) {
        if ("GENERAL_CHAT".equals(intent) || original.matches(".*(改名|修改名称|重新生成|继续|谢谢|你好).*")) return false;
        if ("NETWORK_QA".equals(intent)) return isKnowledgeQuestion(original);
        return "NETWORK_PLANNING".equals(intent) || "IP_VLAN_PLANNING".equals(intent)
                || "DESIGN_EXPLANATION".equals(intent) || "TROUBLESHOOTING".equals(intent) || "TOPOLOGY_GENERATION".equals(intent) || "TOPOLOGY_CHECK".equals(intent) || "RISK_ANALYSIS".equals(intent);
    }

    private boolean isSimpleNetworkQuestion(String value) {
        if (value == null || value.isBlank() || !isKnowledgeQuestion(value)) return false;
        return value.matches(".*(ospf|rip|eigrp|isis|vrrp|hsrp|nat|vlan|stp|rstp|lacp|链路聚合|动态路由|静态路由|子网掩码|vlsm|cidr|dhcp|dns|网关|路由器|交换机|防火墙|广播域|生成树).*");
    }

    private boolean isKnowledgeQuestion(String value) {
        if (value == null || value.isBlank()) return false;
        if (value.matches(".*(当前拓扑|本项目|这台|这个设备|新增|删除|修改拓扑|调整拓扑|移动|规划|设计方案|生成拓扑|创建拓扑).*")) return false;
        return value.matches(".*(是什么|有何作用|有什么作用|为什么|为何|干什么|做什么|区别|原理|含义|用途|负责什么|是否负责|怎么工作|如何工作|是什么关系|对比).*")
                || value.matches(".*[?？].*");
    }
    private Map<String, Object> extractRequirements(String original) {
        Map<String, Object> result = new LinkedHashMap<>();
        Matcher users = USER_COUNT.matcher(original);
        if (users.find()) result.put("userCount", Integer.parseInt(users.group(1)));
        Matcher floors = FLOOR_COUNT.matcher(original);
        if (floors.find()) result.put("floorCount", chineseNumber(floors.group(1)));
        Matcher ports = PORT_COUNT.matcher(original);
        if (ports.find()) result.put("portCount", Integer.parseInt(ports.group(1)));
        Matcher speed = SPEED.matcher(original);
        if (speed.find()) result.put("linkSpeed", speed.group(1) + "x" + speed.group(2) + "G");
        String normalized = original.toLowerCase(Locale.ROOT);
        if (normalized.matches(".*(无线|wifi|wi-fi|wlan|ap).*")) result.put("wirelessCoverage", true);
        if (normalized.matches(".*(内外网隔离|网络隔离|隔离|访客网络|vlan).*")) result.put("networkIsolation", true);
        if (normalized.matches(".*(高可用|冗余|双核心|双链路|容灾).*")) result.put("highAvailability", true);
        if (normalized.matches(".*(服务器机房|服务器区|机房|服务器).*")) result.put("serverRoom", true);
        if (normalized.matches(".*(办公楼|写字楼|办公网络).*")) result.put("scenario", "OFFICE_BUILDING");
        result.put("originalUserMessage", original);
        return result;
    }

    private int chineseNumber(String value) {
        if (value == null || value.isBlank()) return 0;
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { }
        return switch (value) {
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> 0;
        };
    }

    private String json(Map<String, Object> value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ignored) { return "{}"; }
    }

    private int number(Object value) { return value instanceof Number number ? number.intValue() : 0; }
    private boolean isPlanning(String intent) {
        return "NETWORK_PLANNING".equals(intent) || "TOPOLOGY_GENERATION".equals(intent)
                || "TOPOLOGY_MODIFICATION".equals(intent) || "IP_VLAN_PLANNING".equals(intent);
    }
    private double confidence(String intent) { return "GENERAL_CHAT".equals(intent) ? 0.99 : 0.92; }

    public record Decision(String intent, double confidence, String promptTaskType, String originalUserMessage,
                           String structuredRequirements, int userCount, int portCount, boolean requiresProjectContext,
                           boolean requiresKnowledgeBase, boolean planning, boolean prevalidate) { }
}




