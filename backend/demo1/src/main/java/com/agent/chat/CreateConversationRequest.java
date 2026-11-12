package com.agent.chat;

import jakarta.validation.constraints.Size;

public record CreateConversationRequest(@Size(max = 160) String title, Long projectId, String projectPublicId, Long modelId) {
}
