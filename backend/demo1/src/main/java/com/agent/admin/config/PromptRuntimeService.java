package com.agent.admin.config;

import com.agent.ai.AiModel;
import com.agent.ai.AiModelService;
import com.agent.ai.AiRequestOverrides;
import com.agent.common.api.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves the persisted Prompt configuration used by both the admin test and project AI runtime. */
@Service
public class PromptRuntimeService {
    public static final String MODULE = "models.prompts";
    public static final Set<String> TASK_TYPES = Set.of(
            "SYSTEM_BASE", "REQUIREMENT_ANALYSIS", "TOPOLOGY_PLANNING", "NETWORK_PLANNING", "TOPOLOGY_GENERATION",
            "TOPOLOGY_QA", "DESIGN_EXPLANATION", "TOPOLOGY_CHECK", "RISK_ANALYSIS",
            "TOPOLOGY_OPTIMIZATION", "IP_VLAN_PLANNING", "SOLUTION_GENERATION", "NETWORK_QA", "RAG_QA", "CUSTOM"
    );
    public static final Set<String> VARIABLES = Set.of(
            "project_context", "project_name", "project_scale", "topology_nodes", "topology_edges",
            "selected_node", "rag_context", "conversation_context", "model_name", "user_question",
            "requirement_context", "planning_context", "available_topology_resources", "topology_context"
    );
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*}}" );

    private final AdminConfigService configs;
    private final AiModelService models;
    private final ObjectMapper mapper;

    public PromptRuntimeService(AdminConfigService configs, AiModelService models, ObjectMapper mapper) {
        this.configs = configs;
        this.models = models;
        this.mapper = mapper;
    }

    public String canonicalTaskType(String taskType) {
        String normalized = taskType == null ? "" : taskType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "TOPOLOGY_RISK_CHECK" -> "RISK_ANALYSIS";
            case "DOCUMENT_GENERATION" -> "SOLUTION_GENERATION";
            default -> normalized;
        };
    }

    public PromptDefinition activePrompt(String taskType) {
        String requested = canonicalTaskType(taskType);
        if ("NETWORK_QA".equals(requested)) {
            // This path must remain available even when an administrator
            // disables or removes the broader RAG_QA prompt.
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("taskType", "NETWORK_QA");
            values.put("ragEnabled", true);
            values.put("ragTopK", 5);
            values.put("smartRouting", true);
            values.put("routeMode", "SMART");
            values.put("parameters", Map.of("useModelDefaults", true));
            values.put("active", true);
            return new PromptDefinition("__network_qa__", values);
        }
        List<PromptDefinition> sameTask = configs.list(MODULE).stream()
                .filter(item -> !"__catalog_initialized__".equals(String.valueOf(item.get("key"))))
                .filter(item -> requested.equals(canonicalTaskType(text(item, "taskType", ""))))
                .map(item -> new PromptDefinition(String.valueOf(item.get("key")), new LinkedHashMap<>(item)))
                .toList();
        if (sameTask.isEmpty()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROMPT_UNAVAILABLE", "当前 AI 任务尚未配置 Prompt");
        return sameTask.stream().filter(item -> active(item.values())).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROMPT_DISABLED", "当前 AI 任务 Prompt 已停用"));
    }
    public String composeRuntimePrompt(String taskType, Map<String, String> context) {
        PromptDefinition task = activePrompt(taskType);
        return compose(task.values(), context);
    }

    public String compose(Map<String, Object> taskValues, Map<String, String> context) {
        String taskType = canonicalTaskType(text(taskValues, "taskType", ""));
        List<String> parts = new ArrayList<>();
        if (!"SYSTEM_BASE".equals(taskType)) parts.add(render(activePrompt("SYSTEM_BASE").values(), context));
        parts.add(render(taskValues, context));
        return parts.stream().filter(value -> value != null && !value.isBlank()).reduce((left, right) -> left + "\n\n" + right)
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PROMPT_EMPTY", "Prompt 内容不能为空"));
    }

    public List<AiModel> resolveCandidates(Map<String, Object> taskValues, String userTier) {
        return resolveCandidates(taskValues, userTier, null);
    }

    public List<AiModel> resolveCandidates(Map<String, Object> taskValues, String userTier, Long requestedModelId) {
        List<AiModel> available = models.resolveCandidates("chat", requestedModelId, userTier);
        if (available.isEmpty()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE", "当前没有可用的对话模型");
        boolean smartRouting = bool(taskValues.get("smartRouting"), false) || "SMART".equalsIgnoreCase(text(taskValues, "routeMode", ""));
        AiModel configuredPrimary = text(taskValues, "modelId", "").isBlank() ? null : configuredModel(text(taskValues, "modelId", ""));
        AiModel primary = requestedModelId != null ? available.getFirst()
                : smartRouting || configuredPrimary == null ? available.getFirst() : configuredPrimary;
        if (!isReadyForPrompt(primary)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MODEL_TYPE_INCOMPATIBLE", "该模型不支持对话或拓扑任务");

        LinkedHashSet<AiModel> candidates = new LinkedHashSet<>();
        candidates.add(primary);
        String fallbackId = text(taskValues, "fallbackModelId", "");
        if (!fallbackId.isBlank()) {
            AiModel fallback = configuredModel(fallbackId);
            if (isReadyForPrompt(fallback) && !fallback.getId().equals(primary.getId())) candidates.add(fallback);
        }
        available.forEach(candidates::add);
        return candidates.stream().toList();
    }

    public AiRequestOverrides overrides(Map<String, Object> taskValues, AiModel model) {
        Map<String, Object> parameters = map(taskValues.get("parameters"));
        if (parameters.isEmpty() || bool(parameters.get("useModelDefaults"), true)) return AiRequestOverrides.NONE;
        BigDecimal temperature = decimal(parameters.get("temperature"), null, BigDecimal.ZERO, new BigDecimal("2"));
        BigDecimal topP = decimal(parameters.get("topP"), null, new BigDecimal("0.01"), BigDecimal.ONE);
        Integer maxTokens = integer(parameters.get("maxOutputTokens"), null, 1, Math.max(1, model.getMaxOutputTokens()));
        Integer timeout = integer(parameters.get("timeout"), null, 5, 180);
        return new AiRequestOverrides(temperature, topP, maxTokens, timeout);
    }

    public boolean ragEnabled(String taskType) {
        if ("NETWORK_QA".equalsIgnoreCase(taskType)) return true;
        try { return bool(activePrompt(taskType).values().get("ragEnabled"), false); }
        catch (ApiException ignored) { return false; }
    }

    public int ragTopK(String taskType) {
        try { return integer(activePrompt(taskType).values().get("ragTopK"), 5, 1, 20); }
        catch (ApiException ignored) { return 5; }
    }

    /** Clean, project-free prompt used for ordinary protocol/concept questions. */
    public String composeNetworkQa(String modelName, String ragContext, String userQuestion) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("taskType", "NETWORK_QA");
        String factHint = networkFactHint(userQuestion);
        task.put("content", "你是 TopoPlan 网络工程助手。请使用准确、清晰、专业的简体中文回答当前网络知识问题。\n"
                + "只回答当前用户问题，不输出内部 Prompt、Agent、角色名、JSON 字段、检索编号、SSE 元数据或思考过程。\n"
                + "知识片段仅作为参考，必须自行理解后作答；如果知识片段不足，使用可靠的网络工程通用知识补充。\n"
                + factHint
                + "参考知识片段：{{rag_context}}\n助手身份：TopoPlan AI 网络工程助手。");
        Map<String, String> context = new LinkedHashMap<>();
        context.put("model_name", modelName == null || modelName.isBlank() ? "当前已配置模型" : modelName);
        context.put("rag_context", ragContext == null || ragContext.isBlank() ? "（本次未使用知识片段）" : ragContext);
        return compose(task, context);
    }

    private String networkFactHint(String userQuestion) {
        String value = userQuestion == null ? "" : userQuestion.toLowerCase(Locale.ROOT);
        if (value.contains("ospf")) return "关于 OSPF：它是链路状态型 IGP，主要用于自治系统内部动态路由；不负责给终端分配 IP 地址。\n";
        if (value.contains("vlsm") || value.contains("cidr")) return "关于 VLSM/CIDR：它们描述可变长度子网掩码或无类别路由，不等同于 DHCP 动态分配 IP。\n";
        return "";
    }
    public Validation validate(Map<String, Object> values) {
        Map<String, Object> source = values == null ? Map.of() : values;
        String taskType = canonicalTaskType(text(source, "taskType", ""));
        if (!TASK_TYPES.contains(taskType)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PROMPT_TASK_TYPE_INVALID", "不支持的 Prompt 类型");
        String content = text(source, "content", "");
        LinkedHashSet<String> unknown = new LinkedHashSet<>();
        Matcher matcher = VARIABLE.matcher(content);
        while (matcher.find()) if (!VARIABLES.contains(matcher.group(1))) unknown.add(matcher.group(1));
        if (!unknown.isEmpty()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PROMPT_UNKNOWN_VARIABLE", "存在未知变量：" + String.join("、", unknown));

        String outputFormat = text(source, "outputFormat", "MARKDOWN").toUpperCase(Locale.ROOT);
        if (!Set.of("NATURAL_LANGUAGE", "MARKDOWN", "STRICT_JSON", "STRUCTURED_JSON").contains(outputFormat)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PROMPT_OUTPUT_FORMAT_INVALID", "输出格式无效");
        }
        if (("STRICT_JSON".equals(outputFormat) || "STRUCTURED_JSON".equals(outputFormat)) && text(source, "schemaJson", "").length() > 20_000) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PROMPT_SCHEMA_TOO_LARGE", "JSON Schema 不能超过 20000 个字符");
        }
        validateConfiguredModel(text(source, "modelId", ""));
        validateConfiguredModel(text(source, "fallbackModelId", ""));
        Map<String, Object> parameters = map(source.get("parameters"));
        if (!parameters.isEmpty() && !bool(parameters.get("useModelDefaults"), true)) {
            decimal(parameters.get("temperature"), null, BigDecimal.ZERO, new BigDecimal("2"));
            decimal(parameters.get("topP"), null, new BigDecimal("0.01"), BigDecimal.ONE);
            integer(parameters.get("maxOutputTokens"), null, 1, 32768);
            integer(parameters.get("timeout"), null, 5, 180);
        }
        List<String> warnings = new ArrayList<>();
        String normalizedContent = content.toLowerCase(Locale.ROOT);
        if ("MARKDOWN".equals(outputFormat) && (normalizedContent.contains("严格 json") || normalizedContent.contains("必须返回json") || normalizedContent.contains("must return json"))) {
            warnings.add("Prompt 内容要求 JSON，但当前输出格式为 Markdown。");
        }
        return new Validation(taskType, warnings);
    }

    /** Normalizes structured model output and verifies the configured JSON shape before it reaches a consumer. */
    public String validateStructuredOutput(Map<String, Object> taskValues, String rawContent) {
        String outputFormat = text(taskValues, "outputFormat", "MARKDOWN").toUpperCase(Locale.ROOT);
        String taskType = canonicalTaskType(text(taskValues, "taskType", ""));
        if (!Set.of("STRICT_JSON", "STRUCTURED_JSON").contains(outputFormat)
                && !Set.of("TOPOLOGY_GENERATION", "TOPOLOGY_PLANNING", "TOPOLOGY_OPTIMIZATION").contains(taskType)) return rawContent == null ? "" : rawContent.trim();

        try {
            JsonNode output = mapper.readTree(extractJson(rawContent));
            if (output == null || output.isNull()) throw new JsonProcessingException("empty JSON output") { };
            boolean changePlan = Set.of("TOPOLOGY_GENERATION", "TOPOLOGY_PLANNING", "TOPOLOGY_OPTIMIZATION").contains(taskType)
                    && output.isObject() && (output.has("addNodes") || output.has("addEdges")
                    || output.has("nodesToAdd") || output.has("edgesToAdd")
                    || output.has("nodes") || output.has("connections") || output.has("edges")
                    || "TOPOLOGY_PLAN".equals(output.path("action").asText())
                    || "topology_change_plan".equals(output.path("type").asText()));
            if (!changePlan) validateJsonShape(output, text(taskValues, "schemaJson", ""));
            return mapper.writeValueAsString(output);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PROMPT_STRUCTURED_OUTPUT_INVALID", "模型未返回符合 Prompt 输出约束的 JSON");
        }
    }

    public boolean requiresStructuredOutput(Map<String, Object> taskValues) {
        String outputFormat = text(taskValues, "outputFormat", "MARKDOWN").toUpperCase(Locale.ROOT);
        String taskType = canonicalTaskType(text(taskValues, "taskType", ""));
        return "STRICT_JSON".equals(outputFormat) || "STRUCTURED_JSON".equals(outputFormat)
                || Set.of("TOPOLOGY_GENERATION", "TOPOLOGY_PLANNING", "TOPOLOGY_OPTIMIZATION").contains(taskType);
    }

    public String structuredOutputRepairInstruction(Map<String, Object> taskValues, String invalidContent) {
        String schema = text(taskValues, "schemaJson", "").trim();
        String expected = schema.isBlank() ? "返回合法 JSON，且不要使用 Markdown 代码围栏或附加解释。" : "必须匹配以下 JSON Schema 或顶层结构：\n" + schema;
        return "请将下面的模型输出修复为可解析的 JSON。只输出最终 JSON，不要说明、不要 Markdown 代码围栏。\n"
                + expected + "\n\n待修复内容：\n" + (invalidContent == null ? "" : invalidContent);
    }

    public Map<String, String> safeContext(Map<String, String> source) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String variable : VARIABLES) result.put(variable, "[当前调用未提供该上下文]");
        if (source != null) source.forEach((key, value) -> result.put(key, value == null || value.isBlank() ? "[当前调用未提供该上下文]" : value));
        return result;
    }

    public String availableTopologyResources() {
        List<Map<String, Object>> resources = configs.list("knowledge.devices").stream()
                .filter(item -> !Boolean.FALSE.equals(item.get("enabled")))
                .filter(item -> Boolean.TRUE.equals(item.get("allowAi")))
                .map(item -> {
                    Map<String, Object> resource = new LinkedHashMap<>();
                    String type = text(item, "type", text(item, "key", ""));
                    // The topology save contract validates both fields against
                    // the library type. Keep display names separate so models
                    // cannot mistake an English label for deviceType.
                    resource.put("resourceId", text(item, "id", text(item, "key", type)));
                    resource.put("resourceKey", text(item, "key", type));
                    resource.put("type", type);
                    resource.put("name", text(item, "name", ""));
                    resource.put("deviceType", type);
                    resource.put("displayName", text(item, "englishName", text(item, "name", type)));
                    resource.put("category", text(item, "category", ""));
                    Object ports = item.get("ports");
                    resource.put("ports", ports == null ? List.of() : ports);
                    resource.put("defaultPortCount", item.getOrDefault("defaultPortCount", ports instanceof List<?> list ? list.size() : 0));
                    return resource;
                }).toList();
        try { return mapper.writeValueAsString(resources); } catch (JsonProcessingException ignored) { return "[]"; }
    }

    private String render(Map<String, Object> values, Map<String, String> rawContext) {
        Map<String, String> context = safeContext(rawContext);
        Map<String, Object> policy = map(values.get("contextPolicy"));
        String content = text(values, "content", "");
        Matcher matcher = VARIABLE.matcher(content);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String variable = matcher.group(1);
            String replacement = context.getOrDefault(variable, "[当前调用未提供该上下文]");
            if (!enabledForVariable(policy, variable)) replacement = "[该 Prompt 未启用此上下文]";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString().trim();
    }

    private String extractJson(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.startsWith("```")) {
            int firstBreak = normalized.indexOf('\n');
            int closing = normalized.lastIndexOf("```");
            if (firstBreak >= 0 && closing > firstBreak) normalized = normalized.substring(firstBreak + 1, closing).trim();
        }
        if (!normalized.startsWith("{") && !normalized.startsWith("[")) {
            int start = normalized.indexOf('{');
            int end = normalized.lastIndexOf('}');
            if (start >= 0 && end > start) normalized = normalized.substring(start, end + 1).trim();
        }
        return normalized;
    }

    private void validateJsonShape(JsonNode output, String schemaJson) throws JsonProcessingException {
        if (schemaJson == null || schemaJson.isBlank()) return;
        JsonNode schema = mapper.readTree(schemaJson);
        if (schema == null || !schema.isObject()) throw new JsonProcessingException("invalid schema") { };

        Set<String> required = new LinkedHashSet<>();
        JsonNode declaredRequired = schema.path("required");
        if (declaredRequired.isArray()) declaredRequired.forEach(item -> required.add(item.asText()));
        JsonNode properties = schema.path("properties");
        if (required.isEmpty() && properties.isObject()) properties.fieldNames().forEachRemaining(required::add);
        if (required.isEmpty() && !schema.has("$schema") && !schema.has("type")) schema.fieldNames().forEachRemaining(required::add);
        if (required.isEmpty()) return;
        if (!output.isObject()) throw new JsonProcessingException("structured output must be an object") { };
        for (String field : required) {
            if (!output.has(field) || output.get(field).isNull()) throw new JsonProcessingException("missing required field: " + field) { };
        }
    }

    private boolean enabledForVariable(Map<String, Object> policy, String variable) {
        if (policy.isEmpty()) return true;
        String key = switch (variable) {
            case "project_context", "project_name", "project_scale", "requirement_context", "planning_context" -> "project";
            case "topology_nodes", "topology_edges", "topology_context", "available_topology_resources" -> "topology";
            case "selected_node" -> "selectedNode";
            case "conversation_context" -> "conversation";
            case "rag_context" -> "knowledge";
            default -> null;
        };
        return key == null || bool(policy.get(key), true);
    }

    private void validateConfiguredModel(String id) {
        if (id == null || id.isBlank()) return;
        AiModel model = configuredModel(id);
        if (!isChatCapable(model)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MODEL_TYPE_INCOMPATIBLE", "该模型不支持对话/拓扑生成任务");
    }

    private AiModel configuredModel(String id) {
        try { return models.get(Long.valueOf(id.trim())); }
        catch (NumberFormatException exception) { throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PROMPT_MODEL_INVALID", "请选择模型与 API 管理中真实存在的模型"); }
    }

    private boolean isReadyForPrompt(AiModel model) {
        return model != null && model.isEnabled() && "ACTIVE".equalsIgnoreCase(model.getStatus()) && isChatCapable(model);
    }

    private boolean isChatCapable(AiModel model) {
        String type = model.getModelType() == null ? "" : model.getModelType().trim().toLowerCase(Locale.ROOT);
        return "chat".equals(type) || "reasoning".equals(type);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private boolean active(Map<String, Object> value) {
        return bool(value.get("active"), false) || bool(value.get("enabled"), false) || "active".equalsIgnoreCase(text(value, "status", ""));
    }

    private String text(Map<String, Object> values, String key, String fallback) {
        Object value = values == null ? null : values.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private Integer integer(Object value, Integer fallback, int min, int max) {
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        try { return Math.max(min, Math.min(max, new BigDecimal(String.valueOf(value)).intValueExact())); }
        catch (RuntimeException exception) { throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PROMPT_PARAMETER_INVALID", "Prompt 参数超出允许范围"); }
    }

    private BigDecimal decimal(Object value, BigDecimal fallback, BigDecimal min, BigDecimal max) {
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        try {
            BigDecimal number = new BigDecimal(String.valueOf(value));
            if (number.compareTo(min) < 0 || number.compareTo(max) > 0) throw new NumberFormatException();
            return number;
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PROMPT_PARAMETER_INVALID", "Prompt 参数超出允许范围");
        }
    }

    public record PromptDefinition(String id, Map<String, Object> values) { }
    public record Validation(String taskType, List<String> warnings) { }
}







