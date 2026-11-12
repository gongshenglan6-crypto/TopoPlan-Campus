package com.agent.chat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegenerateMessageRequest(@NotNull Long messageId, Long modelId, @Size(max = 80) String clientMessageId) {
}