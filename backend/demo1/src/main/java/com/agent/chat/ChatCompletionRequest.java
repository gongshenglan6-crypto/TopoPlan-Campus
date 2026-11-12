package com.agent.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ChatCompletionRequest(Long conversationId, @NotBlank @Size(max = 8000) String message, Long modelId,
                                    Long projectId, String projectPublicId, String selectedNodeId, String taskType, String clientMessageId, String conversationPublicId,
                                    List<Long> fileIds, Boolean webSearch) {
}
