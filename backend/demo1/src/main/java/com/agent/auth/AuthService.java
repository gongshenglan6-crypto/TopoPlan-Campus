package com.agent.auth;

import com.agent.common.audit.AuditService;
import com.agent.admin.security.RiskEnforcementService;
import com.agent.common.api.ApiException;
import com.agent.login.LoginRecordService;
import com.agent.login.LoginRequestMetadata;
import com.agent.user.User;
import com.agent.user.UserRepository;
import com.agent.subscription.SubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final LoginRecordService loginRecordService;
    private final RiskEnforcementService riskEnforcement;
    private final SubscriptionService subscriptionService;
    private final TokenService tokenService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthService(UserRepository userRepository, AuthenticationManager authenticationManager, PasswordEncoder passwordEncoder,
                       AuditService auditService, LoginRecordService loginRecordService, RiskEnforcementService riskEnforcement,
                       SubscriptionService subscriptionService, TokenService tokenService) {
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.loginRecordService = loginRecordService;
        this.riskEnforcement = riskEnforcement;
        this.subscriptionService = subscriptionService;
        this.tokenService = tokenService;
    }

    @Transactional
    public UserPrincipal register(UserRegisterDTO request, LoginRequestMetadata metadata) {
        String username = request.username().trim();
        String email = request.email().trim();
        ensureLoginIdentifiersAvailable(username, email, null);
        if (subscriptionService.planFor("FREE") == null) {
            throw new ApiException(HttpStatus.CONFLICT, "PLAN_NOT_CONFIGURED", "系统尚未配置 FREE 免费套餐，暂时无法完成注册");
        }
        User user = new User(username, passwordEncoder.encode(request.password()),
                request.nickname().trim(), email, blankToNull(request.avatar()), "USER");
        userRepository.save(user);
        return authenticate(username, request.password(), metadata);
    }

    @Transactional
    public UserPrincipal updateProfile(UserPrincipal principal, UpdateProfileRequest request) {
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is no longer valid"));
        String username = request.username().trim();
        String email = request.email().trim();
        ensureLoginIdentifiersAvailable(username, email, user.getId());
        user.setUsername(username);
        user.setDisplayName(request.displayName().trim());
        user.setEmail(email);
        user.setAvatar(blankToNull(request.avatar()));
        User saved = userRepository.save(user);
        auditService.record("auth", "UPDATE_PROFILE", saved.getId(), "SUCCESS", saved.getUsername());
        return UserPrincipal.from(saved);
    }

    @Transactional
    public UserPrincipal authenticate(String loginIdentifier, String password, LoginRequestMetadata metadata) {
        String normalizedIdentifier = loginIdentifier == null ? "" : loginIdentifier.trim();
        User attemptedUser = userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(normalizedIdentifier, normalizedIdentifier)
                .orElse(null);
        try {
            riskEnforcement.assertLoginAllowed(normalizedIdentifier, attemptedUser, metadata);
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(normalizedIdentifier, password));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            User user = attemptedUser != null ? attemptedUser : userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(normalizedIdentifier, normalizedIdentifier)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "用户名或密码错误"));
            user.markLogin();
            userRepository.save(user);
            loginRecordService.recordSuccessfulLogin(user, normalizedIdentifier, metadata);
            riskEnforcement.recordSuccessfulLogin(user, metadata);
            return UserPrincipal.from(user);
        } catch (ApiException exception) {
            if ("RISK_BLOCKED".equals(exception.getCode())) {
                loginRecordService.recordFailedLogin(normalizedIdentifier, attemptedUser, metadata, "安全策略拦截", "已拦截");
            }
            throw exception;
        } catch (AuthenticationException exception) {
            loginRecordService.recordFailedLogin(normalizedIdentifier, attemptedUser, metadata, failureReason(exception));
            riskEnforcement.recordFailedLogin(normalizedIdentifier, attemptedUser);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "用户名或密码错误");
        }
    }

    public void saveContext(HttpServletRequest request, HttpServletResponse response) {
        securityContextRepository.saveContext(SecurityContextHolder.getContext(), request, response);
        if (SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof UserPrincipal principal) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.setAttribute("session_id", session.getId());
                session.setAttribute("user_id", principal.id());
                session.setAttribute("username", principal.username());
                session.setAttribute("role", principal.role());
                session.setAttribute("login_time", Instant.now().toString());
                session.setAttribute("last_active_time", Instant.now().toString());
            }
        }
    }

    @Transactional
    public void changePassword(UserPrincipal principal, ChangePasswordRequest request) {
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is no longer valid"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CURRENT_PASSWORD_INCORRECT", "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.incrementTokenVersion();
        userRepository.save(user);
        tokenService.revokeAll(user.getId());
        auditService.record("auth", "CHANGE_PASSWORD", user.getId(), "SUCCESS", user.getUsername());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void ensureLoginIdentifiersAvailable(String username, String email, Long currentUserId) {
        if (belongsToAnotherUser(userRepository.findByUsernameIgnoreCase(username), currentUserId)
                || belongsToAnotherUser(userRepository.findByEmailIgnoreCase(username), currentUserId)) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "用户名已存在");
        }
        if (belongsToAnotherUser(userRepository.findByEmailIgnoreCase(email), currentUserId)
                || belongsToAnotherUser(userRepository.findByUsernameIgnoreCase(email), currentUserId)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "邮箱已存在");
        }
    }

    private boolean belongsToAnotherUser(java.util.Optional<User> user, Long currentUserId) {
        return user.filter(existing -> currentUserId == null || !existing.getId().equals(currentUserId)).isPresent();
    }

    private String failureReason(AuthenticationException exception) {
        return exception instanceof DisabledException ? "账号已禁用" : "用户名或密码错误";
    }
}
