package com.agent.file;

import com.agent.auth.UserPrincipal;
import com.agent.project.ProjectService;
import com.agent.common.api.ApiResponse;
import com.agent.common.api.ListResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{projectId}/files")
public class ProjectFileController {
    private final ProjectFileService fileService;
    private final ProjectService projectService;

    public ProjectFileController(ProjectFileService fileService, ProjectService projectService) {
        this.fileService = fileService;
        this.projectService = projectService;
    }

    @PostMapping
    public ApiResponse<ProjectFileResponse> upload(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String projectId,
                                                   @RequestParam("file") MultipartFile file) {
        return new ApiResponse<>(fileService.upload(projectService.resolveOwnedId(projectId, principal.id()), principal.id(), file));
    }

    @GetMapping
    public ApiResponse<ListResponse<ProjectFileResponse>> list(@AuthenticationPrincipal UserPrincipal principal,
                                                                @PathVariable String projectId) {
        return new ApiResponse<>(ListResponse.of(fileService.list(projectService.resolveOwnedId(projectId, principal.id()), principal.id())));
    }
}
