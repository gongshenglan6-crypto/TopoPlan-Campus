package com.agent.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Minimal public request for project topology planning. Project context is resolved server-side. */
public record AiTopologyPlanRequest(
        @NotBlank(message = "网络需求不能为空")
        @Size(max = 8000, message = "网络需求不能超过8000个字符")
        String message,
        String conversationPublicId,
        String selectedNodeId,
        String clientMessageId,
        List<Long> fileIds,
        Boolean webSearch
) {
}
