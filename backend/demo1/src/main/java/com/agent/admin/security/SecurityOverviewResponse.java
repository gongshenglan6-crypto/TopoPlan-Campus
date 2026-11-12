package com.agent.admin.security;

import java.time.Instant;
import java.util.List;

public record SecurityOverviewResponse(List<SecurityMetric> metrics, List<SecurityEvent> events,
                                       String serviceStatus, Instant generatedAt) {
}
