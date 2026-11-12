package com.agent.admin.login;

import com.agent.common.api.ApiResponse;
import com.agent.common.api.ListResponse;
import com.agent.login.LoginRecordResponse;
import com.agent.login.LoginRecordService;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/login-records")
public class AdminLoginRecordController {
    private final LoginRecordService loginRecords;

    public AdminLoginRecordController(LoginRecordService loginRecords) {
        this.loginRecords = loginRecords;
    }

    @GetMapping
    public ApiResponse<ListResponse<LoginRecordResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String accountType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String riskStatus,
            @RequestParam(required = false) String loginMethod,
            @RequestParam(required = false) Instant startAt,
            @RequestParam(required = false) Instant endAt) {
        Page<LoginRecordResponse> records = loginRecords.list(page, size, keyword, userId, accountType, result,
                riskStatus, loginMethod, startAt, endAt);
        return new ApiResponse<>(new ListResponse<>(records.getContent(), records.getTotalElements(),
                records.getNumber(), records.getSize()));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> statistics() {
        return new ApiResponse<>(loginRecords.statistics());
    }
}
