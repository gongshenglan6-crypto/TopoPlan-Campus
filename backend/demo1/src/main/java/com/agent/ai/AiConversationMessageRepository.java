package com.agent.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface AiConversationMessageRepository extends JpaRepository<AiConversationMessage, Long> {
    List<AiConversationMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
    List<AiConversationMessage> findByConversationIdAndUserIdOrderByCreatedAtAsc(Long conversationId, Long userId);
    List<AiConversationMessage> findByConversationIdAndUserIdAndStatusInOrderByCreatedAtAsc(Long conversationId, Long userId, List<String> statuses);
    Optional<AiConversationMessage> findByConversationIdAndUserIdAndClientMessageId(Long conversationId, Long userId, String clientMessageId);
    long countByUserIdAndRoleAndCreatedAtGreaterThanEqual(Long userId, String role, Instant createdAt);
}
