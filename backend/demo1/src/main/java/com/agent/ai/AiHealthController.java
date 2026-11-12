package com.agent.ai;

import com.agent.auth.UserPrincipal;
import com.agent.common.api.ApiResponse;
import com.agent.knowledge.KnowledgeBaseService;
import com.agent.user.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiHealthController {
    private final AiModelService models;
    private final UserRepository users;
    private final KnowledgeBaseService knowledge;

    public AiHealthController(AiModelService models, UserRepository users, KnowledgeBaseService knowledge) {
        this.models = models;
        this.users = users;
        this.knowledge = knowledge;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health(@AuthenticationPrincipal UserPrincipal principal) {
        String tier = users.findById(principal.id()).map(com.agent.user.User::getTier).orElse("free");
        boolean chat;
        try { chat = !models.resolveCandidates("chat", null, tier).isEmpty(); }
        catch (RuntimeException ignored) { chat = false; }
        boolean embedding;
        try { embedding = !models.resolveCandidates("embedding", null, tier).isEmpty(); }
        catch (RuntimeException ignored) { embedding = false; }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", chat ? "ok" : "degraded");
        result.put("chatModelAvailable", chat);
        result.put("embeddingModelAvailable", embedding);
        result.put("ragAvailable", knowledge != null);
        return new ApiResponse<>(result);
    }
}
