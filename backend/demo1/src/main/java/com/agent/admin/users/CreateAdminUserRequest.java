package com.agent.admin.users;

import jakarta.validation.constraints.*;

public record CreateAdminUserRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 120) String displayName,
        @Email @Size(max = 180) String email,
        @Pattern(regexp = "ADMIN|USER") String role,
        @NotBlank @Size(max = 40) String tier,
        @Min(0) @Max(200) Integer dailyQuota) {
}
