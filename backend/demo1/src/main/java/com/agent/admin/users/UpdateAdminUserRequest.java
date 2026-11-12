package com.agent.admin.users;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record UpdateAdminUserRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Size(max = 120) String displayName,
        @Email @Size(max = 180) String email,
        @Pattern(regexp = "ADMIN|USER") String role,
        @Pattern(regexp = "ACTIVE|DISABLED") String status,
        @NotBlank @Size(max = 40) String tier,
        @Min(0) @Max(200) Integer dailyQuota,
        String password,
        Instant updatedAt) {
}
