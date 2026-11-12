package com.agent.admin.users;

import com.agent.common.api.ApiResponse;
import com.agent.common.api.ListResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserService service;

    public AdminUserController(AdminUserService service) { this.service = service; }

    @GetMapping
    public ApiResponse<ListResponse<AdminUserResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Page<AdminUserResponse> result = service.list(keyword, page, size);
        return new ApiResponse<>(new ListResponse<>(result.getContent(), result.getTotalElements(), result.getNumber(), result.getSize()));
    }

    @PostMapping
    public ApiResponse<AdminUserResponse> create(@Valid @RequestBody CreateAdminUserRequest request) { return new ApiResponse<>(service.create(request)); }

    @PatchMapping("/{id}")
    public ApiResponse<AdminUserResponse> update(@PathVariable long id, @Valid @RequestBody UpdateAdminUserRequest request) { return new ApiResponse<>(service.update(id, request)); }

    @PostMapping("/{id}/status")
    public ApiResponse<AdminUserResponse> status(@PathVariable long id, @RequestBody StatusRequest request) { return new ApiResponse<>(service.setStatus(id, request.status())); }

    @PostMapping("/{id}/password")
    public ApiResponse<ResetPasswordResponse> resetPassword(@PathVariable long id) { return new ApiResponse<>(service.resetPassword(id)); }

    public record StatusRequest(String status) { }
}
