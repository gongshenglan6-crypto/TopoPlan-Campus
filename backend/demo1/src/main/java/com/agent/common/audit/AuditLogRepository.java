package com.agent.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByResult(String result);
    long countByCreatedAtGreaterThanEqual(Instant createdAt);
    long countByCreatedAtGreaterThanEqualAndResultNot(Instant createdAt, String result);
}
