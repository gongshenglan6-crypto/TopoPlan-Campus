package com.agent.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 120) String username,
        @NotBlank @Size(min = 5, max = 120) String password,
        @NotBlank @Size(max = 120) String displayName,
        @Email @Size(max = 180) String email) {
}
