package com.agent.ai;

public record AiGatewayResult(String content, long inputTokens, long outputTokens, long latencyMs) {
    public long totalTokens() { return inputTokens + outputTokens; }
}
