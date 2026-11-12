package com.agent.chat;

import java.util.List;

public record ChatReplyResponse(ChatMessageResponse userMessage, ChatMessageResponse assistantMessage, ChatSessionResponse conversation,
                                List<Long> attemptedModelIds, Long resolvedModelId, boolean fallbackUsed) {
    public ChatReplyResponse(ChatMessageResponse userMessage, ChatMessageResponse assistantMessage, ChatSessionResponse conversation) {
        this(userMessage, assistantMessage, conversation, List.of(), assistantMessage == null ? null : assistantMessage.modelId(), false);
    }
}
