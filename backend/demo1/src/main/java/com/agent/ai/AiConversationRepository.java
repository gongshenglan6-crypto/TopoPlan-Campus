package com.agent.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AiConversationRepository extends JpaRepository<AiConversation, Long> {
    @Query("select c from AiConversation c where c.userId = :userId and c.projectId is null and c.status <> :status order by c.pinned desc, c.lastMessageAt desc")
    List<AiConversation> findGeneralByUserIdAndStatusNotOrderByPinnedDescLastMessageAtDesc(@Param("userId") Long userId, @Param("status") String status);
    List<AiConversation> findByUserIdOrderByPinnedDescUpdatedAtDesc(Long userId);
    @Query("select c from AiConversation c where c.projectId = :projectId and c.userId = :userId and c.status <> 'DELETED' order by c.updatedAt desc")
    List<AiConversation> findProjectByProjectIdAndUserIdOrderByUpdatedAtDesc(@Param("projectId") Long projectId, @Param("userId") Long userId);
    List<AiConversation> findByProjectIdAndUserIdOrderByUpdatedAtDesc(Long projectId, Long userId);
    Optional<AiConversation> findByIdAndUserId(Long id, Long userId);
    Optional<AiConversation> findByPublicIdAndUserId(String publicId, Long userId);
    Optional<AiConversation> findByPublicTokenHashAndUserId(String publicTokenHash, Long userId);
}
