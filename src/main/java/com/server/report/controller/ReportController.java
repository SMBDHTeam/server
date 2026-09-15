package com.server.report.controller;

import com.server.auth.service.AuthenticatedUser;
import com.server.auth.web.LoginUser;
import com.server.report.domain.ReportTargetType;
import com.server.report.dto.ReportCheckResponse;
import com.server.report.dto.ReportCreateRequest;
import com.server.report.dto.ReportResponse;
import com.server.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
            description = "사유 유형(reasonType)을 고르고, 기타(OTHER)면 설명(reason)을 함께 보낸다. 처리는 관리자 API 가 한다. "
                    + "같은 대상을 다시 신고하면 409 를 반환한다."
    )
    public ReportResponse report(
            @AuthenticationPrincipal AuthenticatedUser loginUser,
            @Valid @RequestBody ReportCreateRequest request
    ) {
        Long userId = LoginUser.require(loginUser);
        return reportService.report(userId, request);
    }

    @GetMapping("/me")
    @Operation(
            summary = "내 신고 여부",
            description = "이 대상을 이미 신고했는지 알려준다. 신고 화면을 열기 전에 확인해 사유를 다시 고르지 않게 한다."
    )
    public ReportCheckResponse check(
            @AuthenticationPrincipal AuthenticatedUser loginUser,
            @Parameter(description = "POST, COMMENT, USER", example = "POST")
            @RequestParam ReportTargetType targetType,
            @Parameter(description = "신고 대상 ID", example = "7")
            @RequestParam Long targetId
    ) {
        Long userId = LoginUser.require(loginUser);
        return reportService.check(userId, targetType, targetId);
    }
}
