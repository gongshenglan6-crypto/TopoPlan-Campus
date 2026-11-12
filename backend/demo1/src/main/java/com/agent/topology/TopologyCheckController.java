package com.agent.topology;

import com.agent.auth.UserPrincipal;
import com.agent.project.ProjectService;
import com.agent.common.api.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/topology")
public class TopologyCheckController {
    private final TopologyCheckService checks;
    private final ProjectService projectService;
    public TopologyCheckController(TopologyCheckService checks, ProjectService projectService) { this.checks = checks; this.projectService = projectService; }
    @PostMapping("/check")
    public ApiResponse<TopologyCheckResponse> check(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String projectId) {
        return new ApiResponse<>(checks.check(projectService.resolveOwnedId(projectId, principal.id()), principal.id()));
    }
}
