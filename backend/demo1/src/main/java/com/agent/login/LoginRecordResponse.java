package com.agent.login;

import java.time.Instant;

public record LoginRecordResponse(Long id, Long userId, String loginIdentifier, String displayName, String accountType,
                                  String loginMethod, String ipAddress, String location, String device,
                                  String operatingSystem, String browser, String userAgent, String result,
                                  String riskStatus, String failureReason, Instant createdAt) {
    public static LoginRecordResponse from(LoginRecord record) {
        return new LoginRecordResponse(record.getId(), record.getUserId(), record.getLoginIdentifier(),
                record.getDisplayName(), record.getAccountType(), record.getLoginMethod(), record.getIpAddress(),
                record.getLocation(), record.getDevice(), record.getOperatingSystem(), record.getBrowser(),
                record.getUserAgent(), record.getResult(), record.getRiskStatus(), record.getFailureReason(),
                record.getCreatedAt());
    }
}
