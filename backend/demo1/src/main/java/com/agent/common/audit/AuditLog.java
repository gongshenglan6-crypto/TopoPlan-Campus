package com.agent.common.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String actor;
    @Column(nullable = false, length = 60)
    private String module;
    @Column(nullable = false, length = 60)
    private String action;
    private Long targetId;
    @Column(nullable = false, length = 20)
    private String result;
    @Column(length = 20)
    private String level;
    @Column(length = 500)
    private String detail;
    @Column(length = 64)
    private String traceId;
    @Column(nullable = false)
    private Instant createdAt;

    protected AuditLog() { }

    public AuditLog(String actor, String module, String action, Long targetId, String result, String detail) {
        this(actor, module, action, targetId, result, detail, defaultLevel(result, action));
    }

    public AuditLog(String actor, String module, String action, Long targetId, String result, String detail, String level) {
        this(actor, module, action, targetId, result, detail, level, UUID.randomUUID().toString());
    }

    public AuditLog(String actor, String module, String action, Long targetId, String result, String detail, String level, String traceId) {
        this.actor = actor;
        this.module = module;
        this.action = action;
        this.targetId = targetId;
        this.result = result;
        this.detail = detail;
        this.level = level == null ? defaultLevel(result, action) : level;
        this.traceId = traceId;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getActor() { return actor; }
    public String getModule() { return module; }
    public String getAction() { return action; }
    public Long getTargetId() { return targetId; }
    public String getResult() { return result; }
    public String getLevel() { return level; }
    public String getDetail() { return detail; }
    public String getTraceId() { return traceId; }
    public Instant getCreatedAt() { return createdAt; }

    private static String defaultLevel(String result, String action) {
        if ("RISK_BLOCK".equals(action) || "BLOCKED".equals(result)) return "SECURITY";
        return "SUCCESS".equals(result) ? "INFO" : "ERROR";
    }
}
