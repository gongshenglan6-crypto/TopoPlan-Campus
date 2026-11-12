package com.agent.chat;

import com.agent.auth.UserPrincipal;
import com.agent.project.ProjectService;
import com.agent.common.api.ApiResponse;
import com.agent.common.api.ListResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/chat")
public class ProjectChatController {
    private final ChatService chatService;
    private final ProjectService projectService;

    public ProjectChatController(ChatService chatService, ProjectService projectService) {
        this.chatService = chatService;
        this.projectService = projectService;
    }

    @PostMapping("/session")
    public ApiResponse<ChatSessionResponse> ensureSession(@AuthenticationPrincipal UserPrincipal principal,
                                                            @PathVariable String projectId) {
        Long resolvedProjectId = projectService.resolveOwnedId(projectId, principal.id());
        return new ApiResponse<>(chatService.getOrCreateProjectConversation(resolvedProjectId, principal.id()));
    }

    @GetMapping("/sessions")
    public ApiResponse<ListResponse<ChatSessionResponse>> sessions(@AuthenticationPrincipal UserPrincipal principal,
                                                                    @PathVariable String projectId) {
        return new ApiResponse<>(ListResponse.of(chatService.listForProject(projectService.resolveOwnedId(projectId, principal.id()), principal.id())));
    }
}
