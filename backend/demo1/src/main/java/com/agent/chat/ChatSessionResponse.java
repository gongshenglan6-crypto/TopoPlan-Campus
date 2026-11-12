package com.agent.chat;

import com.agent.ai.AiConversation;
import java.time.Instant;

public record ChatSessionResponse(Long id, String publicId, String token, Long projectId, String title, String titleStatus, boolean pinned, String status, Instant updatedAt, Instant lastMessageAt, String conversationType) {
    public static ChatSessionResponse from(ChatSession session) {
        return new ChatSessionResponse(session.getId(), null, null, session.getProjectId(), session.getTitle(), "DEFAULT", session.isPinned(), "ACTIVE", session.getUpdatedAt(), session.getUpdatedAt(), session.getProjectId() == null ? "GENERAL_CHAT" : "PROJECT_CHAT");
    }
    public static ChatSessionResponse from(AiConversation conversation) {
        return new ChatSessionResponse(conversation.getId(), conversation.getPublicId(), conversation.getPublicToken(), conversation.getProjectId(), conversation.getTitle(), conversation.getTitleStatus(), conversation.isPinned(), conversation.getStatus(), conversation.getUpdatedAt(), conversation.getLastMessageAt(), conversation.getConversationType());
    }
}
