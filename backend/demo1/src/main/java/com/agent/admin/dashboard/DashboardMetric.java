package com.agent.admin.dashboard;

public record DashboardMetric(String label, Object value, String suffix, Integer decimals,
                              String trend, String tone, boolean realtime) {
}
