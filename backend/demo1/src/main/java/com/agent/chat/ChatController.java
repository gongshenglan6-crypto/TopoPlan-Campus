package com.agent.chat;

import com.agent.ai.AiModelService;
import com.agent.auth.UserPrincipal;
import com.agent.common.api.ApiResponse;
import com.agent.common.api.ListResponse;
import com.agent.user.UserRepository;
import com.agent.project.ProjectService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;
    private final AiModelService modelService;
    private final UserRepository users;
    private final ProjectService projectService;

    public ChatController(ChatService chatService, AiModelService modelService, UserRepository users, ProjectService projectService) {
        this.chatService = chatService;
        this.modelService = modelService;
        this.users = users;
        this.projectService = projectService;
    }

    @GetMapping("/sessions")
    public ApiResponse<ListResponse<ChatSessionResponse>> sessions(@AuthenticationPrincipal UserPrincipal principal) {
        return new ApiResponse<>(ListResponse.of(chatService.listForUser(principal.id())));
    }

    @GetMapping("/conversations")
    public ApiResponse<ListResponse<ChatSessionResponse>> conversations(@AuthenticationPrincipal UserPrincipal principal) {
        return new ApiResponse<>(ListResponse.of(chatService.listForUser(principal.id())));
    }

    @GetMapping("/models")
    public ApiResponse<ListResponse<java.util.Map<String, Object>>> models(@AuthenticationPrincipal UserPrincipal principal) {
        return new ApiResponse<>(ListResponse.of(modelService.listForUser(userTier(principal.id()))));
    }

    @PostMapping("/conversations")
    public ApiResponse<ChatSessionResponse> createConversation(@AuthenticationPrincipal UserPrincipal principal,
                                                                @Valid @RequestBody CreateConversationRequest request) {
        return new ApiResponse<>(chatService.createConversation(principal.id(), request));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<ListResponse<ChatMessageResponse>> messages(@AuthenticationPrincipal UserPrincipal principal,
                                                                    @PathVariable Long sessionId) {
        return new ApiResponse<>(ListResponse.of(chatService.messages(sessionId, principal.id())));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ApiResponse<ChatReplyResponse> send(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long sessionId,
                                               @Valid @RequestBody SendMessageRequest request) {
        return new ApiResponse<>(chatService.send(sessionId, principal.id(), request));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<ListResponse<ChatMessageResponse>> conversationMessages(@AuthenticationPrincipal UserPrincipal principal,
                                                                                 @PathVariable String conversationId) {
        return new ApiResponse<>(ListResponse.of(chatService.messages(conversationId, principal.id())));
    }

    @PatchMapping("/conversations/{conversationId}")
    public ApiResponse<ChatSessionResponse> updateConversation(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String conversationId,
                                                                @Valid @RequestBody UpdateConversationRequest request) {
        return new ApiResponse<>(chatService.updateConversation(conversationId, principal.id(), request));
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ApiResponse<Void> deleteConversation(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String conversationId) {
        chatService.deleteConversation(conversationId, principal.id());
        return new ApiResponse<>(null);
    }

    @PostMapping("/completions")
    public ApiResponse<ChatReplyResponse> completion(@AuthenticationPrincipal UserPrincipal principal,
                                                      @Valid @RequestBody ChatCompletionRequest request) {
        return new ApiResponse<>(chatService.completeProject(resolvedConversationId(request, principal.id()), principal.id(), request.message(), request.modelId(), resolvedProjectId(request, principal.id()), request.selectedNodeId(), request.taskType(), request.clientMessageId(), request.fileIds(), request.webSearch()));
    }

    @PostMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody ChatCompletionRequest request, HttpServletResponse response) {
        applyStreamHeaders(response);
        return chatService.streamProject(resolvedConversationId(request, principal.id()), principal.id(), request.message(), request.modelId(), resolvedProjectId(request, principal.id()), request.selectedNodeId(), request.taskType(), request.clientMessageId(), request.fileIds(), request.webSearch());
    }

    private Long resolvedConversationId(ChatCompletionRequest request, Long userId) {
        if (request.conversationPublicId() != null && !request.conversationPublicId().isBlank()) {
            return chatService.resolveOwnedConversationId(request.conversationPublicId(), userId);
        }
        if (request.conversationId() != null) return request.conversationId();
        throw new com.agent.common.api.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                "CONVERSATION_REQUIRED", "缺少会话标识");
    }

    private Long resolvedProjectId(ChatCompletionRequest request, Long userId) {
        if (request.projectPublicId() != null && !request.projectPublicId().isBlank()) {
            return projectService.resolveOwnedId(request.projectPublicId(), userId);
        }
        return request.projectId();
    }

    private void applyStreamHeaders(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");
    }

    private String userTier(Long userId) {
        return users.findById(userId).map(com.agent.user.User::getTier).orElse("free");
    }
}
