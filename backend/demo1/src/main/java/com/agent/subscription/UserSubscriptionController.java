package com.agent.subscription;

import com.agent.auth.UserPrincipal;
import com.agent.common.api.ApiException;
import com.agent.common.api.ApiResponse;
import com.agent.common.api.ListResponse;
import com.agent.user.User;
import com.agent.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserSubscriptionController {
    private final UserRepository users;
    private final SubscriptionService subscriptions;

    public UserSubscriptionController(UserRepository users, SubscriptionService subscriptions) {
        this.users = users;
        this.subscriptions = subscriptions;
    }

    @GetMapping("/subscription")
    public ApiResponse<Map<String, Object>> subscription(@AuthenticationPrincipal UserPrincipal principal) {
        User user = users.findById(principal.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
        return new ApiResponse<>(subscriptions.subscriptionFor(user));
    }

    @GetMapping("/plans")
    public ApiResponse<ListResponse<Map<String, Object>>> plans() {
        List<Map<String, Object>> values = subscriptions.plans();
        return new ApiResponse<>(ListResponse.of(values));
    }
}
