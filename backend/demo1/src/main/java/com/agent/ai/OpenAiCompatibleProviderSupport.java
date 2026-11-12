package com.agent.ai;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

abstract class OpenAiCompatibleProviderSupport implements AiProviderAdapter {
    @Override
    public AiProviderRequest createRequest(AiModel model, AiRequestType requestType, List<AiGatewayMessage> messages, boolean stream) {
        return createRequest(model, requestType, messages, stream, AiRequestOverrides.NONE);
    }

    @Override
    public AiProviderRequest createRequest(AiModel model, AiRequestType requestType, List<AiGatewayMessage> messages,
                                           boolean stream, AiRequestOverrides overrides) {
        return switch (requestType) {
            case CHAT -> chatRequest(model, messages, stream, overrides == null ? AiRequestOverrides.NONE : overrides);
            case EMBEDDING -> embeddingRequest(model, messages);
            case SEARCH -> searchRequest(model, messages);
        };
    }

    protected AiProviderRequest chatRequest(AiModel model, List<AiGatewayMessage> messages, boolean stream) {
        return chatRequest(model, messages, stream, AiRequestOverrides.NONE);
    }

    protected AiProviderRequest chatRequest(AiModel model, List<AiGatewayMessage> messages, boolean stream,
                                            AiRequestOverrides overrides) {
        List<Map<String, String>> wireMessages = new ArrayList<>();
        for (AiGatewayMessage message : messages) wireMessages.add(Map.of("role", message.role(), "content", message.content()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model.getModelId());
        payload.put("messages", wireMessages);
        payload.put("temperature", overrides.temperature() == null ? model.getTemperature() : overrides.temperature());
        payload.put("top_p", overrides.topP() == null ? model.getTopP() : overrides.topP());
        payload.put("max_tokens", overrides.maxOutputTokens() == null ? model.getMaxOutputTokens() : overrides.maxOutputTokens());
        payload.put("stream", stream);
        return new AiProviderRequest(ProviderEndpointResolver.endpoint(model.getBaseUrl(), "chat/completions"), payload,
                stream ? "text/event-stream" : "application/json");
    }

    protected AiProviderRequest embeddingRequest(AiModel model, List<AiGatewayMessage> messages) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model.getModelId());
        payload.put("input", messages.stream().map(AiGatewayMessage::content).filter(value -> value != null && !value.isBlank()).toList());
        return new AiProviderRequest(ProviderEndpointResolver.endpoint(model.getBaseUrl(), "embeddings"), payload, "application/json");
    }

    protected AiProviderRequest searchRequest(AiModel model, List<AiGatewayMessage> messages) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model.getModelId());
        payload.put("query", testInput(messages));
        return new AiProviderRequest(ProviderEndpointResolver.endpoint(model.getBaseUrl(), "search"), payload, "application/json");
    }

    @Override
    public AiProviderResponse parseResponse(JsonNode body, AiRequestType requestType, List<AiGatewayMessage> messages) {
        return switch (requestType) {
            case CHAT -> chatResponse(body, messages);
            case EMBEDDING -> embeddingResponse(body, messages);
            case SEARCH -> searchResponse(body, messages);
        };
    }

    protected AiProviderResponse chatResponse(JsonNode body, List<AiGatewayMessage> messages) {
        String content = body.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) content = body.path("choices").path(0).path("text").asText("");
        if (content.isBlank()) throw new AiGatewayException("PROVIDER_ERROR", "模型未返回有效内容");
        return response(content, body.path("usage"), messages, 0);
    }

    protected AiProviderResponse embeddingResponse(JsonNode body, List<AiGatewayMessage> messages) {
        JsonNode vector = body.path("data").path(0).path("embedding");
        if (!vector.isArray() || vector.isEmpty()) throw new AiGatewayException("PROVIDER_ERROR", "Embedding 接口未返回向量数据");
        return response("Embedding vector received", body.path("usage"), messages, 0);
    }

    protected AiProviderResponse searchResponse(JsonNode body, List<AiGatewayMessage> messages) {
        String content = body.path("answer").asText("");
        if (content.isBlank()) content = body.path("output_text").asText("");
        if (content.isBlank()) content = body.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank() && body.path("results").isArray() && !body.path("results").isEmpty()) content = "Search results received";
        if (content.isBlank()) throw new AiGatewayException("PROVIDER_ERROR", "搜索接口未返回有效结果");
        return response(content, body.path("usage"), messages, 0);
    }

    protected AiProviderResponse response(String content, JsonNode usage, List<AiGatewayMessage> messages, long outputFallback) {
        long inputTokens = usageValue(usage, "prompt_tokens", "input_tokens");
        long outputTokens = usageValue(usage, "completion_tokens", "output_tokens");
        long totalTokens = usageValue(usage, "total_tokens", "totalTokens");
        if (totalTokens > 0) {
            // Some providers expose only total_tokens. Preserve that authoritative
            // value by deriving the missing side instead of replacing it with an
            // estimate that would under/over-count the user's quota.
            if (inputTokens <= 0 && outputTokens <= 0) inputTokens = estimateTokens(messages.stream().map(AiGatewayMessage::content).toList());
            if (inputTokens <= 0) inputTokens = Math.max(0, totalTokens - outputTokens);
            if (outputTokens <= 0 || inputTokens + outputTokens != totalTokens) outputTokens = Math.max(0, totalTokens - inputTokens);
        } else {
            if (inputTokens <= 0) inputTokens = estimateTokens(messages.stream().map(AiGatewayMessage::content).toList());
            if (outputTokens <= 0) outputTokens = outputFallback > 0 ? outputFallback : estimateTokens(List.of(content));
        }
        return new AiProviderResponse(content, inputTokens, outputTokens);
    }

    protected String testInput(List<AiGatewayMessage> messages) {
        return messages.stream().map(AiGatewayMessage::content).filter(value -> value != null && !value.isBlank()).reduce((left, right) -> right).orElse("网络拓扑工程模型连接测试");
    }

    protected long usageValue(JsonNode usage, String primary, String fallback) {
        if (usage == null || usage.isMissingNode() || usage.isNull()) return 0;
        return usage.path(primary).asLong(usage.path(fallback).asLong(0));
    }

    protected long estimateTokens(List<String> content) {
        long characters = content.stream().filter(java.util.Objects::nonNull).mapToLong(String::length).sum();
        return Math.max(1, (characters + 3) / 4);
    }
}
