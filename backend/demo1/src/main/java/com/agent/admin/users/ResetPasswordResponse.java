package com.agent.admin.users;

public record ResetPasswordResponse(Long userId, String temporaryPassword) {
}
