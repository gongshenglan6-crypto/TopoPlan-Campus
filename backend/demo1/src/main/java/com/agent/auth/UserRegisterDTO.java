package com.agent.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRegisterDTO(
        @Size(max = 4_000_000) String avatar,
        @NotBlank @Size(max = 120) String nickname,
        @NotBlank @Size(min = 3, max = 120) String username,
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(min = 8, max = 120) String password) {
}
