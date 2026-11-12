package com.agent.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(@NotBlank @Size(max = 8000) String content, Long modelId, @Size(max = 80) String clientMessageId) {
}
