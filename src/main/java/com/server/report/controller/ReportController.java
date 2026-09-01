package com.server.report.controller;

import com.server.auth.service.AuthenticatedUser;
import com.server.auth.web.LoginUser;
import com.server.report.dto.ReportCreateRequest;
import com.server.report.dto.ReportResponse;
import com.server.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "커뮤니티 신고", description = "게시물·댓글·사용자 신고 접수")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "신고",
            description = "접수만 한다. 처리 상태를 바꾸는 관리자 기능은 아직 없다. "
                    + "같은 대상을 다시 신고하면 409 를 반환한다."
    )
    public ReportResponse report(
            @AuthenticationPrincipal AuthenticatedUser loginUser,
            @Valid @RequestBody ReportCreateRequest request
    ) {
        Long userId = LoginUser.require(loginUser);
        return reportService.report(userId, request);
    }
}
