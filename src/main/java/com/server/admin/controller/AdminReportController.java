package com.server.admin.controller;

import com.server.admin.dto.AdminReportDetailResponse;
import com.server.admin.dto.AdminReportListResponse;
import com.server.admin.dto.AdminReportResponse;
import com.server.admin.dto.ReportStatusUpdateRequest;
import com.server.admin.service.AdminReportService;
import com.server.auth.web.CurrentUser;
import com.server.post.domain.ReportStatus;
import com.server.post.service.CommentService;
import com.server.post.service.PostService;
import com.server.report.domain.ReportTargetType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 신고 처리.
 *
 * <p>{@code /api/v1/admin/**} 는 {@code SecurityConfig} 가 {@code hasRole('ADMIN')} 으로
 * 막는다. 사용자 API 인가를 아직 켜지 않은 단계에서도 이 경로만은 처음부터 닫혀 있다.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "관리자 - 신고", description = "신고 조회와 처리, 신고 대상 삭제")
public class AdminReportController {

    private final AdminReportService adminReportService;
    private final PostService postService;
    private final CommentService commentService;

    public AdminReportController(
            AdminReportService adminReportService,
            PostService postService,
            CommentService commentService
    ) {
        this.adminReportService = adminReportService;
        this.postService = postService;
        this.commentService = commentService;
    }

    @GetMapping("/reports")
    @Operation(
            summary = "신고 목록",
            description = "오래된 것부터 반환한다. 최신순이면 방치된 신고가 계속 뒤로 밀린다. "
                    + "status 와 targetType 은 생략하면 거르지 않는다."
    )
    public AdminReportListResponse getReports(
            @Parameter(description = "PENDING, REVIEWING, RESOLVED, REJECTED", example = "PENDING")
            @RequestParam(required = false) ReportStatus status,
            @Parameter(description = "POST, COMMENT, USER", example = "POST")
            @RequestParam(required = false) ReportTargetType targetType,
            @Parameter(example = "0") @RequestParam(required = false) Integer page,
            @Parameter(example = "20") @RequestParam(required = false) Integer size
    ) {
        return adminReportService.getReports(status, targetType, page, size);
    }

    @GetMapping("/reports/{reportId}")
    @Operation(
            summary = "신고 상세",
            description = "신고 내용만으로는 판단할 수 없어 대상 원본을 함께 준다. "
                    + "대상이 이미 지워졌으면 target 이 null 이다."
    )
    public AdminReportDetailResponse getReport(
            @Parameter(example = "12") @PathVariable Long reportId) {
        return adminReportService.getReport(reportId);
    }

    @PatchMapping("/reports/{reportId}")
    @Operation(
            summary = "신고 처리 상태 변경",
            description = "되돌리는 것도 허용한다. 잘못 종결한 신고를 다시 대기로 놓을 수 없으면 "
                    + "같은 내용의 새 신고를 기다리는 수밖에 없다."
    )
    public AdminReportResponse updateStatus(
            @Parameter(example = "12") @PathVariable Long reportId,
            @Valid @RequestBody ReportStatusUpdateRequest request
    ) {
        return adminReportService.updateStatus(reportId, request.status(), CurrentUser.idOrNull());
    }

    @DeleteMapping("/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "게시물 삭제 (관리자)",
            description = "작성자 확인만 건너뛰고 본인 삭제와 같게 처리한다. 소프트 삭제이므로 "
                    + "복구 기한 안에는 작성자가 되살릴 수 있다."
    )
    public void deletePost(@Parameter(example = "7") @PathVariable Long postId) {
        postService.deleteByAdmin(postId);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "댓글 삭제 (관리자)",
            description = "댓글 ID 만으로 지운다. 답글이 남아 있으면 자리를 유지하고 "
                    + "작성자와 내용을 감추는 것은 본인 삭제와 같다."
    )
    public void deleteComment(@Parameter(example = "3") @PathVariable Long commentId) {
        commentService.deleteByAdmin(commentId);
    }
}
