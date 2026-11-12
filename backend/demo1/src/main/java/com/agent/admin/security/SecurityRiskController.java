package com.agent.admin.security;

import com.agent.common.api.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/security")
public class SecurityRiskController {
    private final SecurityRiskService security;

    public SecurityRiskController(SecurityRiskService security) {
        this.security = security;
    }

    @GetMapping("/overview")
    public ApiResponse<SecurityOverviewResponse> overview() { return new ApiResponse<>(security.overview()); }

    @PatchMapping("/events/{eventId}/status")
    public ApiResponse<Map<String, Object>> updateEventStatus(@PathVariable String eventId,
                                                               @RequestBody Map<String, Object> request) {
        String status = String.valueOf(request == null ? "pending" : request.getOrDefault("status", "pending"));
        return new ApiResponse<>(security.updateEventStatus(eventId, status));
    }

    @GetMapping("/risk-policy")
    public ApiResponse<Map<String, Object>> riskPolicy() { return new ApiResponse<>(security.riskPolicy()); }

    @PutMapping("/risk-policy")
    public ApiResponse<Map<String, Object>> saveRiskPolicy(@RequestBody Map<String, Object> value) {
        return new ApiResponse<>(security.saveRiskPolicy(value));
    }
}
