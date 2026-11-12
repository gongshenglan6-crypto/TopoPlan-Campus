package com.agent.auth;

import com.agent.common.api.ApiException;
import com.agent.user.User;
import com.agent.user.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class TokenService {
    private static final Duration ACCESS_LIFETIME = Duration.ofMinutes(15);
    private static final Duration REFRESH_LIFETIME = Duration.ofDays(30);
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final SecureRandom random = new SecureRandom();

    public TokenService(UserRepository users, RefreshTokenRepository refreshTokens, ObjectMapper objectMapper,
                        @Value("${topo.jwt.secret}") String configuredSecret) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.objectMapper = objectMapper;
        this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public IssuedTokens issue(UserPrincipal principal) {
        User user = users.findById(principal.id()).orElseThrow(() -> unauthorized("UNAUTHORIZED", "请先登录"));
        return issueWithNewRefresh(user);
    }

    @Transactional
    public IssuedTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) throw unauthorized("REFRESH_REQUIRED", "登录状态已失效");
        String hash = hash(rawRefreshToken);
        RefreshToken token = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> unauthorized("REFRESH_INVALID", "登录状态已失效"));
        User user = users.findById(token.getUserId()).orElse(null);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            revokeAll(token.getUserId());
            throw unauthorized("ACCOUNT_DISABLED", "账号已被停用，请重新登录");
        }
        if (!token.isUsable(Instant.now())) {
            revokeAll(user.getId());
            throw unauthorized("REFRESH_REUSED", "登录状态已失效，请重新登录");
        }
        token.revoke();
        refreshTokens.save(token);
        return issueWithNewRefresh(user);
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;
        refreshTokens.findByTokenHash(hash(rawRefreshToken)).ifPresent(token -> {
            token.revoke();
            refreshTokens.save(token);
        });
    }

    @Transactional
    public void revokeAccessToken(String rawAccessToken) {
        if (rawAccessToken == null || rawAccessToken.isBlank()) return;
        try {
            Map<String, Object> claims = parse(rawAccessToken);
            revokeAll(numberClaim(claims, "sub").longValue());
        } catch (ApiException ignored) {
            // Logout is deliberately idempotent, including after access-token expiry.
        }
    }

    @Transactional
    public void revokeAll(Long userId) {
        var activeTokens = refreshTokens.findByUserIdAndRevokedAtIsNull(userId);
        activeTokens.forEach(RefreshToken::revoke);
        refreshTokens.saveAll(activeTokens);
    }

    public UserPrincipal validateAccessToken(String token) {
        Map<String, Object> claims = parse(token);
        Number subject = numberClaim(claims, "sub");
        Number version = numberClaim(claims, "token_version");
        long exp = numberClaim(claims, "exp").longValue();
        if (Instant.now().getEpochSecond() >= exp) throw unauthorized("TOKEN_EXPIRED", "登录状态已失效");
        User user = users.findById(subject.longValue()).orElseThrow(() -> unauthorized("UNAUTHORIZED", "请先登录"));
        if (!"ACTIVE".equals(user.getStatus()) || user.getTokenVersion() != version.longValue()) {
            throw unauthorized("UNAUTHORIZED", "请先登录");
        }
        return UserPrincipal.from(user);
    }

    private IssuedTokens issueWithNewRefresh(User user) {
        String raw = randomToken();
        refreshTokens.save(new RefreshToken(UUID.randomUUID().toString(), user.getId(), hash(raw), Instant.now().plus(REFRESH_LIFETIME)));
        return new IssuedTokens(new AuthTokenResponse(createAccessToken(user), ACCESS_LIFETIME.toSeconds(), CurrentUserResponse.from(UserPrincipal.from(user))), raw);
    }

    private String createAccessToken(User user) {
        try {
            String header = URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            long now = Instant.now().getEpochSecond();
            Map<String, Object> payload = new HashMap<>();
            payload.put("sub", user.getId());
            payload.put("jti", UUID.randomUUID().toString());
            payload.put("iat", now);
            payload.put("exp", now + ACCESS_LIFETIME.toSeconds());
            payload.put("role", user.getRole());
            payload.put("token_version", user.getTokenVersion());
            String body = URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
            String signingInput = header + "." + body;
            return signingInput + "." + URL_ENCODER.encodeToString(sign(signingInput));
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "TOKEN_ISSUE_FAILED", "登录状态暂时无法建立");
        }
    }

    private Map<String, Object> parse(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.");
            if (parts.length != 3) throw unauthorized("UNAUTHORIZED", "请先登录");
            String input = parts[0] + "." + parts[1];
            if (!MessageDigest.isEqual(sign(input), URL_DECODER.decode(parts[2]))) throw unauthorized("UNAUTHORIZED", "请先登录");
            return objectMapper.readValue(URL_DECODER.decode(parts[1]), new TypeReference<>() {});
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unauthorized("UNAUTHORIZED", "请先登录");
        }
    }

    private Number numberClaim(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        if (!(value instanceof Number number)) throw unauthorized("UNAUTHORIZED", "请先登录");
        return number;
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

}
