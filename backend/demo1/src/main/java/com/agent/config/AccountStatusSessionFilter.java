package com.agent.config;

import com.agent.auth.UserPrincipal;
import com.agent.common.api.ApiError;
import com.agent.user.User;
import com.agent.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class AccountStatusSessionFilter extends OncePerRequestFilter {
    private final UserRepository users;
    private final ObjectMapper objectMapper;

    public AccountStatusSessionFilter(UserRepository users, ObjectMapper objectMapper) {
        this.users = users;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/")
                || path.equals("/api/auth/login")
                || path.equals("/api/auth/register")
                || path.equals("/api/auth/refresh")
                || path.equals("/api/auth/refresh/revoke")
                || path.equals("/api/auth/csrf")
                || path.equals("/api/auth/logout");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean bearerRequest = request.getHeader("Authorization") != null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        User user = users.findById(principal.id()).orElse(null);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            SecurityContextHolder.clearContext();
            if (!bearerRequest) {
                HttpSession session = request.getSession(false);
                if (session != null) session.invalidate();
                Cookie cookie = new Cookie("TOPOSESSION", "");
                cookie.setPath("/");
                cookie.setHttpOnly(true);
                cookie.setMaxAge(0);
                response.addCookie(cookie);
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    new ApiError("ACCOUNT_DISABLED", "账号已被停用，请重新登录", Map.of(), UUID.randomUUID().toString())));
            return;
        }

        UserPrincipal refreshedPrincipal = UserPrincipal.from(user);
        UsernamePasswordAuthenticationToken refreshed = UsernamePasswordAuthenticationToken.authenticated(
                refreshedPrincipal, refreshedPrincipal.getPassword(), refreshedPrincipal.getAuthorities());
        refreshed.setDetails(authentication.getDetails() instanceof WebAuthenticationDetails details ? details : null);
        SecurityContextHolder.getContext().setAuthentication(refreshed);
        HttpSession session = request.getSession(false);
        if (session != null && !bearerRequest) {
            session.setAttribute("session_id", session.getId());
            session.setAttribute("user_id", user.getId());
            session.setAttribute("username", user.getUsername());
            session.setAttribute("role", user.getRole());
            session.setAttribute("last_active_time", Instant.now().toString());
        }
        filterChain.doFilter(request, response);
    }
}
