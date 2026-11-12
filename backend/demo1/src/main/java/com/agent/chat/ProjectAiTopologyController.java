package com.agent.chat;

import com.agent.auth.UserPrincipal;
import com.agent.project.ProjectService;
import com.agent.common.api.ApiResponse;
import com.agent.common.api.ListResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/** Public project-scoped entry point for AI topology planning. */
@RestController
@RequestMapping("/api/projects/{projectPublicId}/ai")
public class ProjectAiTopologyController {
    private final ChatService chatService;
    private final ProjectService projectService;

    public ProjectAiTopologyController(ChatService chatService, ProjectService projectService) {
        this.chatService = chatService;
        this.projectService = projectService;
    }

    @GetMapping("/conversations/{conversationToken}/messages")
    public ApiResponse<ListResponse<ChatMessageResponse>> messages(@AuthenticationPrincipal UserPrincipal principal,
                                                                    @PathVariable String projectPublicId,
                                                                    @PathVariable String conversationToken) {
        Long projectId = projectService.resolveOwnedId(projectPublicId, principal.id());
        return new ApiResponse<>(ListResponse.of(chatService.projectMessages(conversationToken, principal.id(), projectId)));
    }

    @PostMapping(value = "/plan", produces = "text/event-stream")
    public SseEmitter plan(@AuthenticationPrincipal UserPrincipal principal,
                           @PathVariable String projectPublicId,
                           @Valid @RequestBody AiTopologyPlanRequest request,
                           HttpServletResponse response) {
        Long projectId = projectService.resolveOwnedId(projectPublicId, principal.id());
        ChatSessionResponse conversation;
        String conversationToken = request.conversationPublicId();
        if (conversationToken == null || conversationToken.isBlank()) {
            conversation = chatService.getOrCreateProjectConversation(projectId, principal.id());
        } else {
            conversation = chatService.getProjectConversation(conversationToken.trim(), principal.id(), projectId);
        }
        applyStreamHeaders(response);
        return chatService.streamProject(conversation.id(), principal.id(), request.message(), null,
                projectId, request.selectedNodeId(), "TOPOLOGY_GENERATION", request.clientMessageId() == null || request.clientMessageId().isBlank() ? "plan-" + UUID.randomUUID() : request.clientMessageId(), request.fileIds(), request.webSearch());
    }

    private void applyStreamHeaders(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");
    }
}
