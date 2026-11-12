package com.agent.topology;

import com.agent.auth.UserPrincipal;
import com.agent.project.ProjectService;
import com.agent.common.api.ApiResponse;
import com.agent.common.api.ListResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/topology")
public class TopologyController {
    private final TopologyService topologyService;
    private final ProjectService projectService;

    public TopologyController(TopologyService topologyService, ProjectService projectService) {
        this.topologyService = topologyService;
        this.projectService = projectService;
    }

    @GetMapping
    public ApiResponse<TopologyResponse> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String projectId) {
        return new ApiResponse<>(topologyService.get(projectService.resolveOwnedId(projectId, principal.id()), principal.id()));
    }

    @PutMapping
    public ApiResponse<TopologyResponse> save(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String projectId,
                                               @Valid @RequestBody TopologyRequest request) {
        return new ApiResponse<>(topologyService.save(projectService.resolveOwnedId(projectId, principal.id()), principal.id(), request));
    }

    @GetMapping("/versions")
    public ApiResponse<ListResponse<TopologyVersionResponse>> versions(@AuthenticationPrincipal UserPrincipal principal,
                                                                         @PathVariable String projectId) {
        return new ApiResponse<>(ListResponse.of(topologyService.versions(projectService.resolveOwnedId(projectId, principal.id()), principal.id())));
    }

    @PostMapping({"/apply-plan", "/apply-ai-plan"})
    public ApiResponse<TopologyResponse> applyPlan(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String projectId,
                                                    @Valid @RequestBody TopologyApplyPlanRequest request) {
        return new ApiResponse<>(topologyService.applyPlan(projectService.resolveOwnedId(projectId, principal.id()), principal.id(), request.plan()));
    }
}
