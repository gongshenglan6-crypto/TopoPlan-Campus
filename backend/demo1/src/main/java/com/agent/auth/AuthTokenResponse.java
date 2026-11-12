package com.agent.auth;

public record AuthTokenResponse(String accessToken, long expiresIn, CurrentUserResponse user) {
    public Long getId() { return user.id(); }
    public String getUsername() { return user.username(); }
    public String getDisplayName() { return user.displayName(); }
    public String getEmail() { return user.email(); }
    public String getAvatar() { return user.avatar(); }
    public String getRole() { return user.role(); }
    public String getStatus() { return user.status(); }
}
