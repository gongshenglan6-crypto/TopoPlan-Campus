package com.agent.ai;

public record AiModelTestResult(String provider, String modelId, String requestType, int httpStatus,
                                long latencyMs, long inputTokens, long outputTokens, long totalTokens,
                                String requestUrl, String adapter) {
}
