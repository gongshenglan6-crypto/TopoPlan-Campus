package com.agent.admin.dashboard;

import com.agent.common.audit.AuditLog;

import java.time.Instant;

public record DashboardEvent(Instant time, String title, String detail, String tone) {
    public static DashboardEvent from(AuditLog log) {
        String tone = "SUCCESS".equals(log.getResult()) ? "success" : "warning";
        return new DashboardEvent(log.getCreatedAt(), log.getAction(), log.getActor() + " / " + log.getDetail(), tone);
    }
}
