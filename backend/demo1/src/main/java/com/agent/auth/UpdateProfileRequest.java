package com.agent.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Email @Size(max = 180) String email,
        @Size(max = 4_000_000) String avatar) {
}
