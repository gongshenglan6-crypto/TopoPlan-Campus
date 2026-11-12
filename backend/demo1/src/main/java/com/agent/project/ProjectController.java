package com.agent.project;

import com.agent.auth.UserPrincipal;
import com.agent.common.api.ApiResponse;
import com.agent.common.api.ListResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ApiResponse<ListResponse<ProjectResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return new ApiResponse<>(ListResponse.of(projectService.list(principal.id())));
    }

    @PostMapping
    public ApiResponse<ProjectResponse> create(@AuthenticationPrincipal UserPrincipal principal,
                                               @Valid @RequestBody CreateProjectRequest request) {
        return new ApiResponse<>(projectService.create(principal.id(), request));
    }

    @GetMapping("/{projectId}")
    public ApiResponse<ProjectResponse> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String projectId) {
        return new ApiResponse<>(ProjectResponse.from(projectService.getOwned(projectId, principal.id())));
    }

    @PutMapping("/{projectId}")
    public ApiResponse<ProjectResponse> update(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String projectId,
                                               @Valid @RequestBody UpdateProjectRequest request) {
        return new ApiResponse<>(projectService.update(projectService.resolveOwnedId(projectId, principal.id()), principal.id(), request));
    }

    @PatchMapping("/{projectId}")
    public ApiResponse<ProjectResponse> patch(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String projectId,
                                              @Valid @RequestBody UpdateProjectRequest request) {
        return new ApiResponse<>(projectService.update(projectService.resolveOwnedId(projectId, principal.id()), principal.id(), request));
    }

    @PostMapping("/{projectId}/duplicate")
    public ApiResponse<ProjectResponse> duplicate(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String projectId) {
        return new ApiResponse<>(projectService.duplicate(projectService.resolveOwnedId(projectId, principal.id()), principal.id()));
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String projectId) {
        projectService.delete(projectService.resolveOwnedId(projectId, principal.id()), principal.id());
    }

}
