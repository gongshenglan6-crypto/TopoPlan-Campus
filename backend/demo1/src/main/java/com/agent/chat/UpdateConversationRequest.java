package com.agent.chat;

import jakarta.validation.constraints.Size;

public record UpdateConversationRequest(@Size(max = 160) String title, Boolean pinned) {
}
