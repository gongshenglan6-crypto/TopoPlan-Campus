package com.agent.common.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditService {
    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public AuditLog record(String module, String action, Long targetId, String result, String detail) {
        return record(module, action, targetId, result, detail, null);
    }

    public AuditLog record(String module, String action, Long targetId, String result, String detail, String level) {
        return record(module, action, targetId, result, detail, level, UUID.randomUUID().toString());
    }

    public AuditLog record(String module, String action, Long targetId, String result, String detail, String level, String traceId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = authentication == null ? "system" : authentication.getName();
        String normalizedTraceId = traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
        return repository.save(level == null
                ? new AuditLog(actor, module, action, targetId, result, detail, null, normalizedTraceId)
                : new AuditLog(actor, module, action, targetId, result, detail, level, normalizedTraceId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog recordRiskBlock(Long targetId, String detail) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = authentication == null ? "system" : authentication.getName();
        return repository.save(new AuditLog(actor, "security.risk", "RISK_BLOCK", targetId, "BLOCKED",
                detail, "SECURITY", UUID.randomUUID().toString()));
    }
}
