package com.agent.admin.dashboard;

public record DashboardCapability(String key, String name, long calls, double successRate,
                                  long averageLatencyMs, String status) {
}
