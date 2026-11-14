package com.agent.ai;

import com.agent.common.api.ApiException;
import com.agent.admin.config.AdminConfigService;
import com.agent.admin.config.PromptRuntimeService;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AiModelService {
    @Value("${topo.ai.health.failure-threshold:3}")
    private int healthFailureThreshold;
    @Value("${topo.ai.health.cooldown-seconds:300}")
    private long healthCooldownSeconds;
    private final AiModelRepository models;
    private final AiModelTaskBindingRepository bindings;
    private final ApiKeyCrypto crypto;
    private final AdminConfigService configs;

    public AiModelService(AiModelRepository models, AiModelTaskBindingRepository bindings, ApiKeyCrypto crypto,
                          AdminConfigService configs) {
        this.models = models;
        this.bindings = bindings;
        this.crypto = crypto;
        this.configs = configs;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForAdmin() {
        return models.findByDeletedAtIsNullOrderByPriorityAscUpdatedAtDesc().stream().map(this::toAdminMap).toList();
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> values) {
        String provider = required(values, "provider", "providerName");
        String displayName = required(values, "alias", "displayName");
        String baseUrl = required(values, "baseUrl");
        String modelId = normalizeProviderModelId(provider, baseUrl, required(values, "modelId"));
        String apiKey = required(values, "apiKey");
        if (isApiKeyPlaceholder(apiKey)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "API_KEY_NOT_CONFIGURED", "请输入真实 API Key，不能使用展示占位符");
        AiModel model = new AiModel(provider, displayName, modelId, normalizeModelType(modelId, text(values, "modelType", "chat")), baseUrl, crypto.encrypt(apiKey));
        apply(model, values, false);
        AiModel saved = models.save(model);
        replaceBindings(saved.getId(), compatibleUseCases(saved.getModelType(), useCases(values)));
        return toAdminMap(saved);
    }

    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> values) {
        AiModel model = get(id);
        String previousModelType = model.getModelType();
        if (requestsDisable(values) && model.isEnabled()) ensureAtLeastOneEnabledModel();
        apply(model, values, true);
        AiModel saved = models.save(model);
        if (values != null && (values.containsKey("useCases") || !previousModelType.equals(saved.getModelType()))) {
            Collection<String> requestedUseCases = values.containsKey("useCases")
                    ? useCases(values)
                    : bindings.findByModelId(id).stream().map(AiModelTaskBinding::getBusinessType).toList();
            replaceBindings(id, compatibleUseCases(saved.getModelType(), requestedUseCases));
        }
        return toAdminMap(saved);
    }

    @Transactional
    public Map<String, Object> changeEnabled(Long id, boolean enabled) {
        AiModel model = get(id);
        if (!enabled && model.isEnabled()) ensureAtLeastOneEnabledModel();
        model.setEnabled(enabled);
        model.setStatus(enabled ? "ACTIVE" : "DISABLED");
        // Re-enabling a channel is an explicit administrator action. Do not keep an old runtime cooldown after repair.
        if (enabled) model.markHealthy(0);
        return toAdminMap(models.save(model));
    }

    @Transactional
    public Map<String, Object> setPriority(Long id, int priority) {
        AiModel model = get(id);
        model.setPriority(Math.max(0, priority));
        return toAdminMap(models.save(model));
    }

    @Transactional
    public Map<String, Object> setPrimary(Long id) {
        AiModel model = get(id);
        List<AiModel> sameType = models.findByEnabledTrueAndStatusAndDeletedAtIsNullOrderByPriorityAscUpdatedAtDesc("ACTIVE").stream()
                .filter(candidate -> normalizeModelType(candidate.getModelId(), candidate.getModelType()).equals(normalizeModelType(model.getModelId(), model.getModelType())))
                .sorted(Comparator.comparingInt(AiModel::getPriority).thenComparing(AiModel::getId)).toList();
        sameType.stream().filter(candidate -> !candidate.getId().equals(model.getId())).forEach(candidate -> candidate.setPriority(candidate.getPriority() + 1));
        model.setPriority(1);
        models.saveAll(sameType);
        return toAdminMap(model);
    }

    @Transactional
    public Map<String, Object> movePriority(Long id, int direction) {
        AiModel model = get(id);
        List<AiModel> ordered = models.findByDeletedAtIsNullOrderByPriorityAscUpdatedAtDesc().stream()
                .filter(candidate -> candidate.isEnabled() && "ACTIVE".equalsIgnoreCase(candidate.getStatus()))
                .sorted(Comparator.comparingInt(AiModel::getPriority).thenComparing(AiModel::getId)).toList();
        int index = ordered.indexOf(model);
        int target = index + (direction < 0 ? -1 : 1);
        if (index >= 0 && target >= 0 && target < ordered.size()) {
            AiModel other = ordered.get(target);
            int priority = model.getPriority();
            model.setPriority(other.getPriority());
            other.setPriority(priority);
            models.save(other);
            models.save(model);
        }
        return toAdminMap(model);
    }

    @Transactional
    public Map<String, Object> delete(Long id, boolean force) {
        AiModel model = get(id);
        List<AiModel> compatible = compatibleCandidates(model);
        boolean lastChatModel = model.isEnabled() && isChatType(model) && compatible.isEmpty();
        if (lastChatModel && !force) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_CHAT_MODEL",
                    "当前模型是系统最后一个可用对话模型，删除后用户 AI 功能将暂停。",
                    Map.of("modelType", model.getModelType(), "requiresForce", "true"));
        }

        AiModel replacement = compatible.stream().findFirst().orElse(null);
        migratePromptReferences(model, replacement);
        migrateKnowledgeReferences(model, replacement);
        bindings.deleteByModelId(model.getId());
        bindings.flush();
        model.markDeleted();
        models.saveAndFlush(model);
        configs.delete("models.channels", String.valueOf(model.getId()));
        reorderPriorities();

        Map<String, Object> result = new LinkedHashMap<>(toAdminMap(model));
        result.put("deleted", true);
        result.put("replacementModelId", replacement == null ? null : replacement.getId());
        result.put("replacementModelName", replacement == null ? null : replacement.getDisplayName());
        result.put("remainingModels", listForAdmin());
        return result;
    }

    @Transactional(readOnly = true)
    public AiModel get(Long id) {
        return models.findById(id)
                .filter(model -> model.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MODEL_NOT_FOUND", "模型配置不存在"));
    }

    @Transactional(readOnly = true)
    public String apiKeyFor(AiModel model) {
        String apiKey = crypto.decrypt(model.getEncryptedApiKey());
        // Resolve only dedicated model credentials, never arbitrary process variables.
        if (apiKey != null && apiKey.startsWith("env:")) {
            String name = apiKey.substring(4);
            apiKey = name.matches("TOPO_MODEL_KEY_[A-Z0-9_]+") ? System.getenv(name) : null;
        }
        if (isApiKeyPlaceholder(apiKey)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "API_KEY_NOT_CONFIGURED", "模型 API Key 未配置或仍为展示占位符");
        }
        return apiKey;
    }

    @Transactional
    public void markTestRunning(Long id) {
        AiModel model = get(id);
        model.markTestRunning();
        models.save(model);
    }

    @Transactional
    public void recordTestSuccess(Long id, long latencyMs) {
        AiModel model = get(id);
        model.markTestPassed(latencyMs);
        models.save(model);
    }

    @Transactional
    public void recordTestFailure(Long id, long latencyMs, String errorCode, String errorMessage) {
        AiModel model = get(id);
        String safeMessage = errorMessage == null ? "模型测试失败" : errorMessage.replaceAll("[\\r\\n]+", " ").trim();
        model.markTestFailed(latencyMs, errorCode, safeMessage.length() > 1000 ? safeMessage.substring(0, 1000) : safeMessage);
        model.markRuntimeFailure(errorCode, latencyMs, Math.max(1, healthFailureThreshold), Math.max(1, healthCooldownSeconds));
        models.save(model);
    }

    @Transactional
    public void recordRuntimeSuccess(AiModel model, long latencyMs) {
        model.markHealthy(latencyMs);
        models.save(model);
    }

    @Transactional
    public void recordRuntimeFailure(AiModel model, String errorCode, long latencyMs) {
        model.markRuntimeFailure(errorCode, latencyMs, Math.max(1, healthFailureThreshold), Math.max(1, healthCooldownSeconds));
        models.save(model);
    }

    @Transactional
    public boolean runtimeAvailable(AiModel model) {
        return model != null && model.runtimeAvailable(Instant.now());
    }

    @Transactional
    public void repairKnownModelMetadata() {
        for (AiModel model : models.findAll()) {
            String normalizedModelId = normalizeProviderModelId(model.getProviderName(), model.getBaseUrl(), model.getModelId());
            boolean changed = model.ensureTestDefaults();
            if (!normalizedModelId.equals(model.getModelId())) {
                model.setModelId(normalizedModelId);
                changed = true;
            }
            String normalizedType = normalizeModelType(model.getModelId(), model.getModelType());
            if (!normalizedType.equals(model.getModelType())) {
                model.setModelType(normalizedType);
                changed = true;
            }
            String normalizedTiers = normalizeAllowedTiers(model.getAllowedTiers());
            if (!normalizedTiers.equals(model.getAllowedTiers())) {
                model.setAllowedTiers(normalizedTiers);
                changed = true;
            }
            Set<String> expectedBindings = compatibleUseCases(normalizedType, List.of()).stream()
                    .map(this::toBusinessType).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            Set<String> existingBindings = bindings.findByModelId(model.getId()).stream().map(AiModelTaskBinding::getBusinessType)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (("embedding".equals(normalizedType) || "search".equals(normalizedType)) && !existingBindings.equals(expectedBindings)) {
                replaceBindings(model.getId(), compatibleUseCases(normalizedType, List.of()));
                changed = true;
            }
            if (changed) {
                models.save(model);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<AiModel> resolveCandidates(String businessType, Long requestedModelId, String userTier) {
        AiModel requestedModel = null;
        if (requestedModelId != null) {
            requestedModel = get(requestedModelId);
            if (!requestedModel.isEnabled() || !"ACTIVE".equalsIgnoreCase(requestedModel.getStatus())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "MODEL_DISABLED", "所选模型已停用，暂时不能调用");
            }
            if (!tierAllowed(requestedModel, userTier)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "MODEL_NOT_AVAILABLE", "当前账号不能使用所选模型");
            }
            if (!requestedModel.isUserSelectable()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "MODEL_NOT_AVAILABLE", "该模型不支持用户手动选择");
            }
            if (!supportsBusinessType(requestedModel, businessType)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MODEL_TYPE_INCOMPATIBLE", "所选模型类型不支持当前业务");
            }
        }

        List<AiModel> active = models.findByEnabledTrueAndStatusAndDeletedAtIsNullOrderByPriorityAscUpdatedAtDesc("ACTIVE").stream()
                .filter(model -> tierAllowed(model, userTier))
                .filter(model -> supportsBusinessType(model, businessType))
                .filter(this::runtimeAvailable)
                .toList();
        if (active.isEmpty()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE", "当前没有可用的 AI 模型");

        // The repository query is already ordered by admin priority and, for ties,
        // by the most recently updated channel. Keep that order stable so the
        // router follows the exact priority sequence configured by the admin.
        List<AiModel> candidates = new ArrayList<>(active);
        candidates.sort(Comparator.comparingInt(AiModel::getPriority));

        if (requestedModelId != null) {
            AiModel requested = requestedModel;
            candidates.removeIf(model -> model.getId().equals(requestedModelId));
            candidates.add(0, requested);
        }
        if (requestedModelId != null) {
            AiModel requested = candidates.stream().filter(model -> model.getId().equals(requestedModelId)).findFirst().orElse(null);
            if (requested != null) { candidates.remove(requested); candidates.add(0, requested); }
        }
        return candidates;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForUser(String userTier) {
        return models.findByEnabledTrueAndStatusAndDeletedAtIsNullOrderByPriorityAscUpdatedAtDesc("ACTIVE").stream()
                .filter(AiModel::isUserSelectable)
                .filter(model -> tierAllowed(model, userTier))
                .filter(model -> supportsBusinessType(model, "chat"))
                .filter(this::runtimeAvailable)
                .map(model -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("id", model.getId());
                    value.put("displayName", model.getDisplayName());
                    value.put("modelType", model.getModelType());
                    value.put("description", model.getDescription() == null ? model.getProviderName() : model.getDescription());
                    return value;
                }).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> toAdminMap(AiModel model) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", model.getId());
        value.put("key", String.valueOf(model.getId()));
        value.put("provider", model.getProviderName());
        value.put("providerName", model.getProviderName());
        value.put("alias", model.getDisplayName());
        value.put("displayName", model.getDisplayName());
        value.put("modelId", model.getModelId());
        value.put("modelType", model.getModelType());
        value.put("baseUrl", model.getBaseUrl());
        value.put("status", model.getStatus());
        value.put("contextLimit", model.getContextWindow());
        value.put("contextWindow", model.getContextWindow());
        value.put("maxTokens", model.getMaxOutputTokens());
        value.put("maxOutputTokens", model.getMaxOutputTokens());
        value.put("temperature", model.getTemperature());
        value.put("topP", model.getTopP());
        value.put("maxConcurrency", model.getMaxConcurrency());
        value.put("concurrency", model.getCurrentConcurrency());
        value.put("rpm", model.getRpmLimit());
        value.put("tpm", model.getTpmLimit());
        value.put("timeout", model.getTimeoutSeconds());
        value.put("inputPrice", model.getInputTokenPrice());
        value.put("outputPrice", model.getOutputTokenPrice());
        value.put("cachedTokenPrice", model.getCachedTokenPrice());
        value.put("quotaMultiplier", model.getQuotaMultiplier());
        value.put("currency", model.getCurrency());
        value.put("priceUnit", model.getPriceUnit());
        value.put("active", model.isEnabled());
        value.put("enabled", model.isEnabled());
        value.put("priority", model.getPriority());
        value.put("healthStatus", model.getHealthStatus());
        value.put("lastSuccessAt", model.getLastSuccessAt());
        value.put("lastFailureAt", model.getLastFailureAt());
        value.put("consecutiveFailures", model.getConsecutiveFailures());
        value.put("lastErrorCode", model.getLastErrorCode());
        value.put("healthCooldownUntil", model.getHealthCooldownUntil());
        value.put("userSelectable", model.isUserSelectable());
        value.put("allowedTiers", model.getAllowedTiers());
        value.put("description", model.getDescription());
        value.put("latency", model.getLastLatencyMs());
        value.put("lastTestStatus", model.getLastTestStatus());
        value.put("lastTestAt", model.getLastTestAt());
        value.put("lastTestLatency", model.getLastTestLatencyMs());
        value.put("lastTestErrorCode", model.getLastTestErrorCode());
        value.put("lastTestErrorMessage", model.getLastTestErrorMessage());
        value.put("apiKeyConfigured", model.getEncryptedApiKey() != null && !model.getEncryptedApiKey().isBlank());
        value.put("useCases", bindings.findByModelId(model.getId()).stream().map(AiModelTaskBinding::getBusinessType).map(this::toUiUseCase).toList());
        value.put("updatedAt", model.getUpdatedAt());
        value.put("deletedAt", model.getDeletedAt());
        return value;
    }

    private boolean isChatType(AiModel model) {
        return "chat".equalsIgnoreCase(model.getModelType()) || "reasoning".equalsIgnoreCase(model.getModelType());
    }

    private List<AiModel> compatibleCandidates(AiModel deleted) {
        return models.findByEnabledTrueAndStatusAndDeletedAtIsNullOrderByPriorityAscUpdatedAtDesc("ACTIVE").stream()
                .filter(model -> !model.getId().equals(deleted.getId()))
                .filter(model -> isChatType(deleted) ? isChatType(model) : deleted.getModelType().equalsIgnoreCase(model.getModelType()))
                .sorted(Comparator.comparingInt(AiModel::getPriority).thenComparing(AiModel::getId))
                .toList();
    }

    private void migratePromptReferences(AiModel deleted, AiModel replacement) {
        String deletedId = String.valueOf(deleted.getId());
        String replacementId = replacement == null ? "" : String.valueOf(replacement.getId());
        Set<String> compatibleIds = compatibleCandidates(deleted).stream().map(model -> String.valueOf(model.getId())).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (Map<String, Object> item : configs.list(PromptRuntimeService.MODULE)) {
            String key = String.valueOf(item.get("key"));
            if (key.isBlank() || "__catalog_initialized__".equals(key)) continue;
            Map<String, Object> payload = new LinkedHashMap<>(item);
            boolean changed = false;
            String currentFallback = String.valueOf(payload.getOrDefault("fallbackModelId", ""));
            if (deletedId.equals(String.valueOf(payload.getOrDefault("modelId", "")))) {
                String promoted = compatibleIds.contains(currentFallback) ? currentFallback : replacementId;
                payload.put("modelId", promoted);
                if (promoted.equals(currentFallback)) {
                    payload.put("fallbackModelId", compatibleIds.stream().filter(candidate -> !candidate.equals(promoted)).findFirst().orElse(""));
                }
                changed = true;
            }
            if (deletedId.equals(String.valueOf(payload.getOrDefault("fallbackModelId", "")))) {
                String currentMain = String.valueOf(payload.getOrDefault("modelId", ""));
                payload.put("fallbackModelId", replacementId.equals(currentMain) ? "" : replacementId);
                changed = true;
            }
            if (changed) {
                payload.remove("id"); payload.remove("key"); payload.remove("module"); payload.remove("configured"); payload.remove("updatedAt");
                configs.save(PromptRuntimeService.MODULE, key, payload);
            }
        }
    }

    private void migrateKnowledgeReferences(AiModel deleted, AiModel replacement) {
        Map<String, Object> current = configs.get("knowledge.rag", "default");
        if (!Boolean.TRUE.equals(current.get("configured")) && current.size() <= 3) return;
        String replacementName = replacement == null ? "" : replacement.getDisplayName();
        String deletedId = String.valueOf(deleted.getId());
        String[] keys = {"embeddingModel", "rerankModel"};
        Map<String, Object> payload = new LinkedHashMap<>(current);
        boolean changed = false;
        for (String key : keys) {
            String value = String.valueOf(payload.getOrDefault(key, ""));
            if (value.equals(deletedId) || value.equals(deleted.getModelId()) || value.equals(deleted.getDisplayName())) {
                payload.put(key, replacementName);
                changed = true;
            }
        }
        if (changed) {
            payload.remove("id"); payload.remove("key"); payload.remove("module"); payload.remove("configured"); payload.remove("updatedAt");
            configs.save("knowledge.rag", "default", payload);
        }
    }

    private void reorderPriorities() {
        List<AiModel> active = models.findByDeletedAtIsNullOrderByPriorityAscUpdatedAtDesc().stream()
                .filter(AiModel::isEnabled).sorted(Comparator.comparing(AiModel::getModelType).thenComparingInt(AiModel::getPriority).thenComparing(AiModel::getId)).toList();
        Map<String, Integer> counters = new LinkedHashMap<>();
        for (AiModel model : active) model.setPriority(counters.merge(model.getModelType().toLowerCase(Locale.ROOT), 1, Integer::sum));
        models.saveAll(active);
    }

    private void apply(AiModel model, Map<String, Object> values, boolean partial) {
        if (values == null) return;
        String value;
        boolean runtimeConfigChanged = false;
        if ((value = optional(values, "provider", "providerName")) != null) {
            runtimeConfigChanged |= !value.trim().equals(model.getProviderName());
            model.setProviderName(value);
        }
        if ((value = optional(values, "alias", "displayName")) != null) model.setDisplayName(value);
        if ((value = optional(values, "modelId")) != null) {
            runtimeConfigChanged |= !value.trim().equals(model.getModelId());
            model.setModelId(value.trim());
        }
        if ((value = optional(values, "baseUrl")) != null) {
            runtimeConfigChanged |= !value.trim().equals(model.getBaseUrl());
            model.setBaseUrl(value);
        }
        if ((value = optional(values, "apiKey")) != null && !isApiKeyPlaceholder(value)) {
            runtimeConfigChanged = true;
            model.setEncryptedApiKey(crypto.encrypt(value));
        }
        if ((value = optional(values, "status")) != null) {
            String status = value.toUpperCase(Locale.ROOT);
            model.setStatus(status);
            if ("ACTIVE".equals(status) || "ENABLED".equals(status)) model.setEnabled(true);
            if ("DISABLED".equals(status) || "INACTIVE".equals(status)) model.setEnabled(false);
        }
        if (values.containsKey("active")) model.setEnabled(bool(values.get("active"), model.isEnabled()));
        if (values.containsKey("enabled")) model.setEnabled(bool(values.get("enabled"), model.isEnabled()));
        if (values.containsKey("active") || values.containsKey("enabled")) model.setStatus(model.isEnabled() ? "ACTIVE" : "DISABLED");
        if (values.containsKey("contextLimit")) model.setContextWindow(integer(values.get("contextLimit"), model.getContextWindow(), 1));
        if (values.containsKey("contextWindow")) model.setContextWindow(integer(values.get("contextWindow"), model.getContextWindow(), 1));
        if (values.containsKey("maxTokens")) model.setMaxOutputTokens(integer(values.get("maxTokens"), model.getMaxOutputTokens(), 1));
        if (values.containsKey("maxOutputTokens")) model.setMaxOutputTokens(integer(values.get("maxOutputTokens"), model.getMaxOutputTokens(), 1));
        if (values.containsKey("temperature")) model.setTemperature(decimal(values.get("temperature"), model.getTemperature(), BigDecimal.ZERO));
        if (values.containsKey("topP")) model.setTopP(decimal(values.get("topP"), model.getTopP(), BigDecimal.ZERO));
        if (values.containsKey("maxConcurrency")) model.setMaxConcurrency(integer(values.get("maxConcurrency"), model.getMaxConcurrency(), 1));
        if (values.containsKey("concurrency")) model.setCurrentConcurrency(integer(values.get("concurrency"), model.getCurrentConcurrency(), 0));
        if (values.containsKey("rpm")) model.setRpmLimit(integer(values.get("rpm"), model.getRpmLimit(), 1));
        if (values.containsKey("tpm")) model.setTpmLimit(longValue(values.get("tpm"), model.getTpmLimit(), 1));
        if (values.containsKey("timeout")) model.setTimeoutSeconds(integer(values.get("timeout"), model.getTimeoutSeconds(), 1));
        if (values.containsKey("inputPrice")) model.setInputTokenPrice(decimal(values.get("inputPrice"), model.getInputTokenPrice(), BigDecimal.ZERO));
        if (values.containsKey("outputPrice")) model.setOutputTokenPrice(decimal(values.get("outputPrice"), model.getOutputTokenPrice(), BigDecimal.ZERO));
        if (values.containsKey("cachedTokenPrice")) model.setCachedTokenPrice(decimal(values.get("cachedTokenPrice"), model.getCachedTokenPrice(), BigDecimal.ZERO));
        if (values.containsKey("quotaMultiplier")) model.setQuotaMultiplier(decimal(values.get("quotaMultiplier"), model.getQuotaMultiplier(), BigDecimal.ZERO));
        if ((value = optional(values, "currency")) != null) model.setCurrency(value.toUpperCase(Locale.ROOT));
        if (values.containsKey("priceUnit")) model.setPriceUnit(longValue(values.get("priceUnit"), model.getPriceUnit(), 1));
        if (values.containsKey("priority")) model.setPriority(integer(values.get("priority"), model.getPriority(), 0));
        if (values.containsKey("userSelectable")) model.setUserSelectable(bool(values.get("userSelectable"), model.isUserSelectable()));
        if ((value = optional(values, "allowedTiers")) != null) model.setAllowedTiers(value);
        if ((value = optional(values, "description")) != null) model.setDescription(value);
        model.setModelId(normalizeProviderModelId(model.getProviderName(), model.getBaseUrl(), model.getModelId()));
        model.setModelType(normalizeModelType(model.getModelId(), optional(values, "modelType") == null ? model.getModelType() : optional(values, "modelType")));
        if (runtimeConfigChanged) model.markHealthy(0);
    }

    private void replaceBindings(Long modelId, Collection<String> requested) {
        bindings.deleteByModelId(modelId);
        bindings.flush();
        bindings.saveAll(requested.stream().map(this::toBusinessType).filter(value -> !value.isBlank()).distinct()
                .map(value -> new AiModelTaskBinding(modelId, value)).toList());
    }
    private Collection<String> useCases(Map<String, Object> values) {
        Object raw = values == null ? null : values.get("useCases");
        if (raw instanceof Collection<?> collection) return collection.stream().map(String::valueOf).toList();
        if (raw instanceof String value && !value.isBlank()) return List.of(value.split(","));
        return List.of("ai-chat");
    }

    private Collection<String> compatibleUseCases(String modelType, Collection<String> requested) {
        return switch (normalizeModelType(null, modelType)) {
            case "embedding" -> List.of("knowledge-embedding");
            case "search" -> List.of("web-search");
            default -> requested == null || requested.isEmpty() ? List.of("ai-chat") : requested;
        };
    }

    private boolean supportsBusinessType(AiModel model, String businessType) {
        String type = normalizeModelType(model.getModelId(), model.getModelType());
        String normalizedBusinessType = toBusinessType(businessType);
        if ("embedding".equals(normalizedBusinessType)) return "embedding".equals(type);
        if ("web_search".equals(normalizedBusinessType)) return "search".equals(type);
        return "chat".equals(type) || "reasoning".equals(type);
    }

    private String normalizeModelType(String modelId, String configuredType) {
        String normalizedId = modelId == null ? "" : modelId.trim().toLowerCase(Locale.ROOT);
        if (normalizedId.contains("embedding") || normalizedId.contains("bge-")) return "embedding";
        if (normalizedId.contains("search") || normalizedId.contains("serp")) return "search";
        String type = configuredType == null ? "" : configuredType.trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "reasoning", "embedding", "search", "chat" -> type;
            default -> normalizedId.contains("reasoner") || normalizedId.contains("coder") || normalizedId.contains("-r1") ? "reasoning" : "chat";
        };
    }

    private String normalizeProviderModelId(String providerName, String baseUrl, String modelId) {
        String value = modelId == null ? "" : modelId.trim();
        String endpoint = baseUrl == null ? "" : baseUrl.toLowerCase(Locale.ROOT);
        if (isGlmProvider(providerName) || endpoint.contains("bigmodel.cn")) {
            return value.toLowerCase(Locale.ROOT);
        }
        if (endpoint.contains("siliconflow") && isQwenProvider(providerName)
                && "qwen2.5-7b-instruct".equalsIgnoreCase(value)) {
            return "Qwen/Qwen2.5-7B-Instruct";
        }
        if (isQwenProvider(providerName) && "qwen2.5-7b-instruct".equalsIgnoreCase(value)) {
            return "qwen2.5-7b-instruct";
        }
        return value;
    }

    private boolean isQwenProvider(String providerName) {
        String provider = providerName == null ? "" : providerName.trim().toLowerCase(Locale.ROOT);
        return provider.contains("qwen") || provider.contains("alibaba") || provider.contains("aliyun") || provider.contains("dashscope");
    }

    private boolean isGlmProvider(String providerName) {
        String provider = providerName == null ? "" : providerName.trim().toLowerCase(Locale.ROOT);
        return provider.contains("glm") || provider.contains("zhipu") || provider.contains("bigmodel") || provider.contains("智谱");
    }

    private boolean isApiKeyPlaceholder(String value) {
        if (value == null) return true;
        String normalized = value.trim();
        return normalized.isBlank()
                || normalized.equalsIgnoreCase("configured")
                || normalized.equals("已配置")
                || normalized.matches("[＊*•·\\s]+")
                || normalized.equalsIgnoreCase("masked");
    }

    private boolean requestsDisable(Map<String, Object> values) {
        if (values == null) return false;
        if (values.containsKey("active") && !bool(values.get("active"), true)) return true;
        if (values.containsKey("enabled") && !bool(values.get("enabled"), true)) return true;
        String status = optional(values, "status");
        return "DISABLED".equalsIgnoreCase(status) || "INACTIVE".equalsIgnoreCase(status);
    }

    private void ensureAtLeastOneEnabledModel() {
        if (models.countByEnabledTrueAndDeletedAtIsNull() <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "MODEL_LAST_ENABLED", "至少保留一个启用模型，无法停用当前模型。");
        }
    }
    private boolean tierAllowed(AiModel model, String tier) {
        if (model.getAllowedTiers() == null || model.getAllowedTiers().isBlank()) return true;
        String normalizedTier = switch (tier == null ? "FREE" : tier.trim().toUpperCase(Locale.ROOT)) {
            case "STANDARD", "TEAM", "EDUCATION" -> "standard";
            case "PRO", "PREMIUM", "ADVANCED", "ENTERPRISE" -> "pro";
            default -> "free";
        };
        return List.of(model.getAllowedTiers().toLowerCase(Locale.ROOT).split(",")).stream().map(String::trim).anyMatch(normalizedTier::equals);
    }
    private String normalizeAllowedTiers(String value) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        for (String tier : String.valueOf(value == null ? "" : value).split(",")) {
            String normalized = switch (tier.trim().toUpperCase(Locale.ROOT)) {
                case "STANDARD", "TEAM", "EDUCATION" -> "standard";
                case "PRO", "PREMIUM", "ADVANCED", "ENTERPRISE" -> "pro";
                case "FREE", "BASIC", "NORMAL" -> "free";
                default -> "";
            };
            if (!normalized.isBlank()) result.add(normalized);
        }
        return result.isEmpty() ? "free,standard,pro" : String.join(",", result);
    }
    private String toBusinessType(String value) {
        return switch (value == null ? "" : value.trim()) {
            case "ai-chat", "chat" -> "chat";
            case "requirements-analysis", "requirement_analysis" -> "requirement_analysis";
            case "topology-generation", "topology_generation" -> "topology_generation";
            case "network-plan", "network_plan" -> "network_plan";
            case "risk-analysis", "risk_analysis" -> "risk_analysis";
            case "document-generation", "document_generation" -> "document_generation";
            case "knowledge-embedding", "embedding" -> "embedding";
            case "web-search", "search" -> "web_search";
            default -> value == null ? "" : value.trim();
        };
    }
    private String toUiUseCase(String value) {
        return switch (value) {
            case "chat" -> "ai-chat";
            case "requirement_analysis" -> "requirements-analysis";
            case "topology_generation" -> "topology-generation";
            case "network_plan" -> "network-plan";
            case "risk_analysis" -> "risk-analysis";
            case "document_generation" -> "document-generation";
            case "embedding" -> "knowledge-embedding";
            case "web_search" -> "web-search";
            default -> value;
        };
    }
    private String required(Map<String, Object> values, String... names) {
        String value = optional(values, names);
        if (value == null) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "请补全模型配置");
        return value;
    }
    private String optional(Map<String, Object> values, String... names) {
        if (values == null) return null;
        for (String name : names) {
            Object value = values.get(name);
            if (value != null && !String.valueOf(value).trim().isBlank()) return String.valueOf(value).trim();
        }
        return null;
    }
    private String text(Map<String, Object> values, String name, String fallback) { String value = optional(values, name); return value == null ? fallback : value; }
    private int integer(Object value, int fallback, int minimum) { try { return Math.max(minimum, new BigDecimal(String.valueOf(value)).intValue()); } catch (RuntimeException ignored) { return fallback; } }
    private long longValue(Object value, long fallback, long minimum) { try { return Math.max(minimum, new BigDecimal(String.valueOf(value)).longValue()); } catch (RuntimeException ignored) { return fallback; } }
    private BigDecimal decimal(Object value, BigDecimal fallback, BigDecimal minimum) { try { return new BigDecimal(String.valueOf(value)).max(minimum); } catch (RuntimeException ignored) { return fallback; } }
    private boolean bool(Object value, boolean fallback) { return value instanceof Boolean bool ? bool : value == null ? fallback : Boolean.parseBoolean(String.valueOf(value)); }
}
