package com.agent.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    List<ChatSession> findByOwnerIdOrderByPinnedDescUpdatedAtDesc(Long ownerId);
    List<ChatSession> findByProjectIdAndOwnerIdOrderByUpdatedAtDesc(Long projectId, Long ownerId);
    Optional<ChatSession> findByIdAndOwnerId(Long id, Long ownerId);
}
