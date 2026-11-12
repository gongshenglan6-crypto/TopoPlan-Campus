package com.agent.chat;

import com.agent.ai.AiConversationMessage;
import java.time.Instant;

public record ChatMessageResponse(Long id, Long userId, String role, String content, Instant createdAt, Long modelId, long inputTokens, long outputTokens, long totalTokens, String status, String clientMessageId) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(message.getId(), null, message.getRole(), message.getContent(), message.getCreatedAt(), null, 0, 0, 0, "COMPLETE", null);
    }
    public static ChatMessageResponse from(AiConversationMessage message) {
        return new ChatMessageResponse(message.getId(), message.getUserId(), message.getRole(), message.getContent(), message.getCreatedAt(),
                null, message.getInputTokens(), message.getOutputTokens(), message.getTotalTokens(), clientStatus(message.getStatus(), message.getRole()), message.getClientMessageId());
    }
    private static String clientStatus(String status, String role) {
        boolean userMessage = "user".equalsIgnoreCase(role);
        if (status == null) return userMessage ? "sent" : "complete";
        return switch (status.toUpperCase()) {
            case "RECEIVED" -> userMessage ? "sending" : "streaming";
            case "PENDING" -> userMessage ? "sending" : "streaming";
            case "FAILED" -> "failed";
            case "INTERRUPTED" -> "interrupted";
            case "INVALID", "SUPERSEDED" -> "invalid";
            default -> userMessage ? "sent" : "complete";
        };
    }
}
