package com.agent.admin.dashboard;

import java.util.List;
import java.time.Instant;
import java.util.Map;

public record DashboardResponse(List<DashboardMetric> metrics, List<DashboardPoint> successRatePoints,
                                List<DashboardKeyword> keywordBars, List<DashboardEvent> latestEvents,
                                Map<Integer, List<DashboardTrendPoint>> trends,
                                List<DashboardDistributionItem> creationModes,
                                List<DashboardDistributionItem> deviceTypes,
                                List<DashboardCapability> aiCapabilities,
                                DashboardResourceStats resourceStats,
                                String serviceStatus,
                                Instant generatedAt) {
}
