package com.agent.auth;

public record IssuedTokens(AuthTokenResponse response, String refreshToken) {
}
