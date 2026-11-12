package com.agent.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {
    List<AiUsageLog> findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(Instant createdAt);
    List<AiUsageLog> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(Instant from, Instant to);
    List<AiUsageLog> findByUserIdAndCreatedAtGreaterThanEqual(Long userId, Instant createdAt);
}
