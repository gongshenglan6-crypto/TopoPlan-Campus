package com.agent.admin.users;

import com.agent.user.User;
import java.time.Instant;
import java.util.Map;

public record AdminUserResponse(Long id, String username, String displayName, String email,
                                String role, String status, String tier, int dailyQuota,
                                Instant createdAt, Instant updatedAt, Instant lastLoginAt,
                                Map<String, Object> subscription, Map<String, Object> usageSummary) {
    public static AdminUserResponse from(User user, Map<String, Object> subscription, Map<String, Object> usageSummary) {
        return new AdminUserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(),
                user.getRole(), user.getStatus(), user.getTier(), user.getDailyQuota(),
                user.getCreatedAt(), user.getUpdatedAt(), user.getLastLoginAt(), subscription, usageSummary);
    }
}
