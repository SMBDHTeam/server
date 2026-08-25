package com.server.admin.service;

import com.server.admin.dto.AdminReportDetailResponse;
import com.server.admin.dto.AdminReportListResponse;
import com.server.admin.dto.AdminReportResponse;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.post.domain.Comment;
import com.server.post.domain.Post;
import com.server.post.domain.ReportStatus;
import com.server.post.repository.CommentRepository;
import com.server.post.repository.PostRepository;
import com.server.report.domain.Report;
import com.server.report.domain.ReportTargetType;
import com.server.report.repository.ReportRepository;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 신고 처리.
 *
 * <p>신고 내용만으로는 판단할 수 없어 상세에서 대상 원본을 함께 읽는다. 대상이 이미
 * 지워졌으면 그대로 알린다. 신고 접수 뒤 작성자가 스스로 지웠거나 다른 관리자가 먼저
 * 조치한 경우이며, 그 사실을 감추면 관리자가 사라진 대상을 계속 찾게 된다.
 */
@Service
public class AdminReportService {

    private static final Logger log = LoggerFactory.getLogger(AdminReportService.class);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    public AdminReportService(
            ReportRepository reportRepository,
            PostRepository postRepository,
            CommentRepository commentRepository,
            UserRepository userRepository
    ) {
        this.reportRepository = reportRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AdminReportListResponse getReports(
            ReportStatus status, ReportTargetType targetType, Integer page, Integer size) {
        int resolvedPage = page == null || page < 0 ? 0 : page;
        int resolvedSize = size == null || size <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(size, MAX_PAGE_SIZE);

        List<Report> reports = reportRepository.findForAdmin(
                status, targetType, PageRequest.of(resolvedPage, resolvedSize));

        // 전체 건수를 함께 준다. 없으면 화면이 페이지 수를 계산할 수 없어 다음 페이지가
        // 비어 있는지 눌러 봐야 안다.
        return new AdminReportListResponse(
                reports.stream().map(AdminReportResponse::from).toList(),
                reportRepository.countForAdmin(status, targetType));
    }

    @Transactional(readOnly = true)
    public AdminReportDetailResponse getReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        return new AdminReportDetailResponse(
                AdminReportResponse.from(report), loadTarget(report));
    }

    /**
     * 처리 상태를 바꾼다.
     *
     * <p>되돌리는 것도 허용한다. 잘못 종결한 신고를 다시 대기로 놓을 수 없으면 관리자가
     * 같은 내용의 새 신고가 들어오기를 기다리는 수밖에 없다.
     */
    @Transactional
    public AdminReportResponse updateStatus(Long reportId, ReportStatus status, Long adminId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        User admin = userRepository.findByIdAndDeletedAtIsNull(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        report.handle(status, admin);
        log.info("Report status changed. reportId={}, status={}, adminId={}",
                reportId, status, adminId);
        return AdminReportResponse.from(report);
    }

    /**
     * 대상 원본을 읽는다.
     *
     * <p>삭제된 것도 찾는다. 신고 대상이 지워졌다는 사실 자체가 관리자에게 필요한 정보다.
     */
    private AdminReportDetailResponse.Target loadTarget(Report report) {
        return switch (report.getTargetType()) {
            case POST -> postRepository.findById(report.getTargetId())
                    .map(post -> new AdminReportDetailResponse.Target(
                            post.getId(),
                            author(post),
                            post.getContent(),
                            post.getDeletedAt() != null))
                    .orElse(null);
            case COMMENT -> commentRepository.findById(report.getTargetId())
                    .map(comment -> new AdminReportDetailResponse.Target(
                            comment.getId(),
                            author(comment),
                            comment.getContent(),
                            comment.getDeletedAt() != null))
                    .orElse(null);
            case USER -> userRepository.findById(report.getTargetId())
                    .map(user -> new AdminReportDetailResponse.Target(
                            user.getId(),
                            new AdminReportResponse.Reporter(user.getId(), user.getNickname()),
                            user.getNickname(),
                            user.getDeletedAt() != null))
                    .orElse(null);
        };
    }

    private AdminReportResponse.Reporter author(Post post) {
        return new AdminReportResponse.Reporter(
                post.getUser().getId(), post.getUser().getNickname());
    }

    private AdminReportResponse.Reporter author(Comment comment) {
        return new AdminReportResponse.Reporter(
                comment.getUser().getId(), comment.getUser().getNickname());
    }
}
