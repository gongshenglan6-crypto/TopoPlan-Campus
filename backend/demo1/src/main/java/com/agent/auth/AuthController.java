package com.agent.auth;

import com.agent.common.api.ApiResponse;
import com.agent.login.LoginRequestMetadata;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final TokenService tokenService;

    public AuthController(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request,
                                                   HttpServletRequest httpRequest,
                                                   HttpServletResponse httpResponse) {
        UserPrincipal principal = authService.authenticate(request.username(), request.password(), LoginRequestMetadata.from(httpRequest));
        rotateSessionId(httpRequest);
        authService.saveContext(httpRequest, httpResponse);
        IssuedTokens issued = tokenService.issue(principal);
        setRefreshCookie(httpResponse, issued.refreshToken());
        return new ApiResponse<>(issued.response());
    }

    @PostMapping("/register")
    public ApiResponse<AuthTokenResponse> register(@Valid @RequestBody UserRegisterDTO request,
                                                      HttpServletRequest httpRequest,
                                                      HttpServletResponse httpResponse) {
        UserPrincipal principal = authService.register(request, LoginRequestMetadata.from(httpRequest));
        rotateSessionId(httpRequest);
        authService.saveContext(httpRequest, httpResponse);
        IssuedTokens issued = tokenService.issue(principal);
        setRefreshCookie(httpResponse, issued.refreshToken());
        return new ApiResponse<>(issued.response());
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        IssuedTokens issued = tokenService.refresh(readCookie(request, "TOPO_REFRESH"));
        setRefreshCookie(response, issued.refreshToken());
        return new ApiResponse<>(issued.response());
    }

    @PostMapping("/refresh/revoke")
    public ApiResponse<Void> revokeRefresh(HttpServletRequest request, HttpServletResponse response) {
        tokenService.revoke(readCookie(request, "TOPO_REFRESH"));
        response.addHeader("Set-Cookie", "TOPO_REFRESH=; Path=/api/auth/refresh; Max-Age=0; HttpOnly; SameSite=Lax");
        return new ApiResponse<>(null);
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return new ApiResponse<>(CurrentUserResponse.from(principal));
    }

    @GetMapping("/csrf")
    public ApiResponse<String> csrf(CsrfToken token, HttpServletResponse response) {
        Cookie csrfCookie = new Cookie("XSRF-TOKEN", token.getToken());
        csrfCookie.setPath("/");
        csrfCookie.setHttpOnly(false);
        response.addCookie(csrfCookie);
        return new ApiResponse<>(token.getToken());
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal, request);
        return new ApiResponse<>(null);
    }

    @PutMapping("/profile")
    public ApiResponse<CurrentUserResponse> updateProfile(@AuthenticationPrincipal UserPrincipal principal,
                                                          @Valid @RequestBody UpdateProfileRequest request,
                                                          HttpServletRequest httpRequest,
                                                          HttpServletResponse httpResponse) {
        UserPrincipal updatedPrincipal = authService.updateProfile(principal, request);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                updatedPrincipal, updatedPrincipal.getPassword(), updatedPrincipal.getAuthorities()));
        authService.saveContext(httpRequest, httpResponse);
        return new ApiResponse<>(CurrentUserResponse.from(updatedPrincipal));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        tokenService.revoke(readCookie(request, "TOPO_REFRESH"));
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            tokenService.revokeAccessToken(authorization.substring(7).trim());
        }
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        Cookie cookie = new Cookie("TOPOSESSION", "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        response.addHeader("Set-Cookie", "TOPO_REFRESH=; Path=/api/auth/refresh; Max-Age=0; HttpOnly; SameSite=Lax");
        return new ApiResponse<>(null);
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    private void setRefreshCookie(HttpServletResponse response, String value) {
        boolean secure = Boolean.parseBoolean(System.getenv().getOrDefault("TOPO_SESSION_COOKIE_SECURE", "false"));
        response.addHeader("Set-Cookie", "TOPO_REFRESH=" + value + "; Path=/api/auth/refresh; Max-Age=2592000; HttpOnly; SameSite=Lax" + (secure ? "; Secure" : ""));
    }

    private void rotateSessionId(HttpServletRequest request) {
        if (request.getSession(false) != null) request.changeSessionId();
    }
}
