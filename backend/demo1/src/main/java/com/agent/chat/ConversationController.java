package com.agent.chat;

import com.agent.auth.UserPrincipal;
import com.agent.common.api.ApiResponse;
import com.agent.common.api.ListResponse;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final ChatService chatService;

    public ConversationController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public ApiResponse<ListResponse<ChatSessionResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return new ApiResponse<>(ListResponse.of(chatService.listForUser(principal.id())));
    }

    @PostMapping
    public ApiResponse<ChatSessionResponse> create(@AuthenticationPrincipal UserPrincipal principal,
                                                    @Valid @RequestBody CreateConversationRequest request) {
        return new ApiResponse<>(chatService.createGeneralConversation(principal.id(), request));
    }

    @GetMapping("/{conversationToken}")
    public ApiResponse<ChatSessionResponse> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String conversationToken) {
        return new ApiResponse<>(chatService.getGeneralConversation(conversationToken, principal.id()));
    }

    @PatchMapping("/{conversationToken}")
    public ApiResponse<ChatSessionResponse> update(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String conversationToken,
                                                    @Valid @RequestBody UpdateConversationRequest request) {
        return new ApiResponse<>(chatService.updateGeneralConversation(conversationToken, principal.id(), request));
    }

    @DeleteMapping("/{conversationToken}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String conversationToken) {
        chatService.deleteGeneralConversation(conversationToken, principal.id());
        return new ApiResponse<>(null);
    }

    @GetMapping("/{conversationToken}/messages")
    public ApiResponse<ListResponse<ChatMessageResponse>> messages(@AuthenticationPrincipal UserPrincipal principal,
                                                                     @PathVariable String conversationToken) {
        return new ApiResponse<>(ListResponse.of(chatService.generalMessages(conversationToken, principal.id())));
    }

    @PostMapping("/{conversationToken}/messages")
    public ApiResponse<ChatReplyResponse> send(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String conversationToken,
                                               @Valid @RequestBody SendMessageRequest request) {
        return new ApiResponse<>(chatService.sendGeneral(conversationToken, principal.id(), request));
    }
    @PatchMapping("/{conversationToken}/messages/{messageId}")
    public ApiResponse<ChatMessageResponse> editMessage(@AuthenticationPrincipal UserPrincipal principal,
                                                          @PathVariable String conversationToken,
                                                          @PathVariable Long messageId,
                                                          @Valid @RequestBody EditMessageRequest request) {
        return new ApiResponse<>(chatService.editGeneralUserMessage(conversationToken, principal.id(), messageId, request.content()));
    }

    @PostMapping(value = "/{conversationToken}/stream", produces = "text/event-stream")
    public SseEmitter stream(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String conversationToken,
                             @Valid @RequestBody SendMessageRequest request, HttpServletResponse response) {
        applyStreamHeaders(response);
        return chatService.streamGeneral(conversationToken, principal.id(), request);
    }
    @PostMapping(value = "/{conversationToken}/regenerate", produces = "text/event-stream")
    public SseEmitter regenerate(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String conversationToken,
                                 @Valid @RequestBody RegenerateMessageRequest request, HttpServletResponse response) {
        applyStreamHeaders(response);
        return chatService.regenerateGeneral(conversationToken, principal.id(), request);
    }
    private void applyStreamHeaders(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");
    }

}
