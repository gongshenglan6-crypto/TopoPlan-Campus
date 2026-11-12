package com.agent.admin.users;

import com.agent.common.api.ApiException;
import com.agent.common.audit.AuditService;
import com.agent.user.User;
import com.agent.user.UserRepository;
import com.agent.auth.TokenService;
import com.agent.subscription.SubscriptionService;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AdminUserService {
    private static final String[] PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%".split("");
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SubscriptionService subscriptions;
    private final SecureRandom random = new SecureRandom();
    private final TokenService tokenService;

    public AdminUserService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService,
                            SubscriptionService subscriptions, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.subscriptions = subscriptions;
        this.tokenService = tokenService;
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> list(String keyword, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> users = keyword == null || keyword.isBlank()
                ? userRepository.findAll(pageable)
                : userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
                keyword.trim(), keyword.trim(), keyword.trim(), pageable);
        return users.map(this::response);
    }

    @Transactional
    public AdminUserResponse create(CreateAdminUserRequest request) {
        String username = request.username().trim();
        String email = blankToNull(request.email());
        ensureUnique(username, email, null);
        User user = new User(username, passwordEncoder.encode(request.password()), request.displayName().trim(), email,
                defaultValue(request.role(), "USER"));
        subscriptions.assignTier(user, request.tier());
        user.setDailyQuota(request.dailyQuota() == null ? 0 : request.dailyQuota());
        User saved = userRepository.save(user);
        auditService.record("users", "CREATE", saved.getId(), "SUCCESS", saved.getUsername());
        return response(saved);
    }

    @Transactional
    public AdminUserResponse update(long id, UpdateAdminUserRequest request) {
        User user = find(id);
        if (request.updatedAt() != null && user.getUpdatedAt() != null && !request.updatedAt().equals(user.getUpdatedAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "STALE_VERSION", "User changed; refresh before saving");
        }
        String username = request.username().trim();
        String email = blankToNull(request.email());
        ensureUnique(username, email, id);
        String nextRole = defaultValue(request.role(), user.getRole());
        String nextStatus = defaultValue(request.status(), user.getStatus());
        boolean roleOrStatusChanged = !user.getRole().equals(nextRole) || !user.getStatus().equals(nextStatus);
        if ("ADMIN".equals(user.getRole()) && "ACTIVE".equals(user.getStatus())
                && (!"ADMIN".equals(nextRole) || "DISABLED".equals(nextStatus))
                && userRepository.countByRoleAndStatus("ADMIN", "ACTIVE") <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "FINAL_ADMIN", "At least one active administrator is required");
        }
        user.setUsername(username);
        user.setDisplayName(request.displayName().trim());
        user.setEmail(email);
        user.setRole(nextRole);
        user.setStatus(nextStatus);
        subscriptions.assignTier(user, request.tier());
        user.setDailyQuota(request.dailyQuota() == null ? user.getDailyQuota() : request.dailyQuota());
        boolean securityChanged = false;
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            securityChanged = true;
        }
        if (roleOrStatusChanged) securityChanged = true;
        if (securityChanged) user.incrementTokenVersion();
        User saved = userRepository.save(user);
        if (securityChanged) tokenService.revokeAll(saved.getId());
        auditService.record("users", "UPDATE", saved.getId(), "SUCCESS", saved.getUsername());
        return response(saved);
    }

    @Transactional
    public AdminUserResponse setStatus(long id, String status) {
        User user = find(id);
        String next = status == null ? "" : status.trim().toUpperCase();
        if (!next.equals("ACTIVE") && !next.equals("DISABLED")) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "status must be ACTIVE or DISABLED");
        }
        if ("ADMIN".equals(user.getRole()) && "ACTIVE".equals(user.getStatus()) && "DISABLED".equals(next)
                && userRepository.countByRoleAndStatus("ADMIN", "ACTIVE") <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "FINAL_ADMIN", "At least one active administrator is required");
        }
        boolean securityChanged = !next.equals(user.getStatus());
        user.setStatus(next);
        if (securityChanged) user.incrementTokenVersion();
        User saved = userRepository.save(user);
        if (securityChanged) tokenService.revokeAll(saved.getId());
        auditService.record("users", "STATUS", saved.getId(), "SUCCESS", next);
        return response(saved);
    }

    @Transactional
    public ResetPasswordResponse resetPassword(long id) {
        User user = find(id);
        String password = randomPassword(14);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.incrementTokenVersion();
        userRepository.save(user);
        tokenService.revokeAll(user.getId());
        auditService.record("users", "RESET_PASSWORD", id, "SUCCESS", user.getUsername());
        return new ResetPasswordResponse(id, password);
    }

    private User find(long id) { return userRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found")); }
    private AdminUserResponse response(User user) {
        Map<String, Object> summary = subscriptions.adminUserSummary(user);
        @SuppressWarnings("unchecked") Map<String, Object> subscription = summary.get("subscription") instanceof Map<?, ?> value ? (Map<String, Object>) value : Map.of();
        return AdminUserResponse.from(user, subscription, summary);
    }
    private void ensureUnique(String username, String email, Long id) {
        if (belongsToAnotherUser(userRepository.findByUsernameIgnoreCase(username), id)
                || belongsToAnotherUser(userRepository.findByEmailIgnoreCase(username), id))
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "Username already exists");
        if (email != null && (belongsToAnotherUser(userRepository.findByEmailIgnoreCase(email), id)
                || belongsToAnotherUser(userRepository.findByUsernameIgnoreCase(email), id))) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "Email already exists");
        }
    }
    private boolean belongsToAnotherUser(java.util.Optional<User> user, Long id) {
        return user.filter(existing -> id == null || !existing.getId().equals(id)).isPresent();
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String defaultValue(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(); }
    private String randomPassword(int length) { StringBuilder result = new StringBuilder(length); for (int i = 0; i < length; i++) result.append(PASSWORD_CHARS[random.nextInt(PASSWORD_CHARS.length)]); return result.toString(); }
}
