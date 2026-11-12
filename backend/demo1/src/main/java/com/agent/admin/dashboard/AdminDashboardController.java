package com.agent.admin.dashboard;

import com.agent.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {
    private final AdminDashboardService service;

    public AdminDashboardController(AdminDashboardService service) { this.service = service; }

    @GetMapping
    public ApiResponse<DashboardResponse> overview() { return new ApiResponse<>(service.getOverview()); }
}
