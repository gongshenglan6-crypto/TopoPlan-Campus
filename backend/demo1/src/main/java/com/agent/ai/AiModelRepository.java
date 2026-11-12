package com.agent.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiModelRepository extends JpaRepository<AiModel, Long> {
    List<AiModel> findByEnabledTrueAndStatusAndDeletedAtIsNullOrderByPriorityAscUpdatedAtDesc(String status);
    List<AiModel> findByDeletedAtIsNullOrderByPriorityAscUpdatedAtDesc();
    long countByEnabledTrueAndDeletedAtIsNull();
}
