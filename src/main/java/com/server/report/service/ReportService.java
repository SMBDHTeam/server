package com.server.report.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.post.repository.CommentRepository;
import com.server.post.repository.PostRepository;
import com.server.report.domain.Report;
import com.server.report.domain.ReportTargetType;
import com.server.report.dto.ReportCreateRequest;
import com.server.report.dto.ReportResponse;
import com.server.report.repository.ReportRepository;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    public ReportService(
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

    @Transactional
    public ReportResponse report(Long reporterId, ReportCreateRequest request) {
        User reporter = userRepository.findByIdAndDeletedAtIsNull(reporterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        requireTargetExists(request.targetType(), request.targetId());
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                reporterId, request.targetType(), request.targetId())) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }

        Report report = reportRepository.save(new Report(
                reporter, request.targetType(), request.targetId(), request.reason()));
        return ReportResponse.from(report);
    }

    /** {@code target_id}에 외래키가 없으므로 대상이 실제로 있는지 여기서 확인한다. */
    private void requireTargetExists(ReportTargetType targetType, Long targetId) {
        boolean exists = switch (targetType) {
            case POST -> postRepository.existsByIdAndDeletedAtIsNull(targetId);
            case COMMENT -> commentRepository.findByIdAndDeletedAtIsNull(targetId).isPresent();
            case USER -> userRepository.findByIdAndDeletedAtIsNull(targetId).isPresent();
        };
        if (!exists) {
            throw new BusinessException(switch (targetType) {
                case POST -> ErrorCode.POST_NOT_FOUND;
                case COMMENT -> ErrorCode.COMMENT_NOT_FOUND;
                case USER -> ErrorCode.USER_NOT_FOUND;
            });
        }
    }
}
