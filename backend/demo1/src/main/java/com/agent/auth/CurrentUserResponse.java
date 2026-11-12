package com.agent.auth;

public record CurrentUserResponse(Long id, String username, String displayName, String email, String avatar, String role, String status) {
    public static CurrentUserResponse from(UserPrincipal principal) {
        return new CurrentUserResponse(principal.id(), principal.username(), principal.displayName(), principal.email(),
                principal.avatar(), principal.role(), principal.status());
    }
}
