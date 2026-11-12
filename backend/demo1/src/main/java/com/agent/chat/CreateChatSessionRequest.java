package com.agent.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateChatSessionRequest(@NotBlank @Size(max = 160) String title) {
}
