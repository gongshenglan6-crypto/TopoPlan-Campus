package com.agent.login;

import jakarta.servlet.http.HttpServletRequest;

/** Request attributes retained with an account-level login audit event. */
public record LoginRequestMetadata(String ipAddress, String userAgent) {
    public static LoginRequestMetadata from(HttpServletRequest request) {
        String ipAddress = request == null || request.getRemoteAddr() == null || request.getRemoteAddr().isBlank()
                ? "未知IP"
                : request.getRemoteAddr().trim();
        String userAgent = request == null ? "" : request.getHeader("User-Agent");
        return new LoginRequestMetadata(ipAddress, userAgent == null ? "" : userAgent.trim());
    }
}
