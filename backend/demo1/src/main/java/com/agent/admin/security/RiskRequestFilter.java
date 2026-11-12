package com.agent.admin.security;

import com.agent.common.api.ApiError;
import com.agent.common.api.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RiskRequestFilter extends OncePerRequestFilter {
    private final RiskEnforcementService risk;
    private final ObjectMapper mapper;

    public RiskRequestFilter(RiskEnforcementService risk, ObjectMapper mapper) {
        this.risk = risk;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean protectedDownload = path.startsWith("/api/admin/") && path.contains("/download");
        return !path.startsWith("/api/") || (path.startsWith("/api/admin/") && !protectedDownload) || path.equals("/api/auth/csrf");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String ipAddress = request.getRemoteAddr();
        String path = request.getRequestURI();
        try {
            risk.assertRequestAllowed(ipAddress, request.getHeader("X-API-Key"));
            if (path.contains("/download")) risk.assertExportAllowed(ipAddress);
        } catch (ApiException exception) {
            response.setStatus(exception.getStatus().value());
            response.setContentType("application/json;charset=UTF-8");
            mapper.writeValue(response.getWriter(), new ApiError(exception.getCode(), exception.getMessage(),
                    exception.getDetails(), UUID.randomUUID().toString()));
            return;
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            risk.recordRequestOutcome(ipAddress, path, response.getStatus());
        }
    }
}
