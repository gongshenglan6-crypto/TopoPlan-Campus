package com.agent.admin.security;

import java.time.Instant;

public record SecurityEvent(String id, Instant timestamp, String level, String type, String subject,
                            String userId, String maskedIp, String device, String summary, String rule,
                            String traceId, String status, boolean sensitive) {
}
