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
import com.server.user.service.ActiveUserReader;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final ActiveUserReader activeUserReader;

    public ReportService(
            ReportRepository reportRepository,
            PostRepository postRepository,
            CommentRepository commentRepository,
            UserRepository userRepository,
            ActiveUserReader activeUserReader
    ) {
        this.reportRepository = reportRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.activeUserReader = activeUserReader;
    }

    @Transactional
    public ReportResponse report(Long reporterId, ReportCreateRequest request) {
        User reporter = activeUserReader.require(reporterId);

        requireTargetExists(request.targetType(), request.targetId());
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                reporterId, request.targetType(), request.targetId())) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }

        try {
            Report report = reportRepository.saveAndFlush(new Report(
                    reporter, request.targetType(), request.targetId(), request.reason()));
            return ReportResponse.from(report);
        } catch (DataIntegrityViolationException exception) {
            // 위 확인과 저장 사이에 같은 신고가 들어오면 uk_reports_reporter_target 에 걸린다.
            // 확인만으로는 막을 수 없는 경합이라 제약에 맡기고 같은 응답으로 돌려준다.
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }
    }

    /**
     * {@code target_id}에 외래키가 없으므로 대상이 실제로 있는지 여기서 확인한다.
     *
     * <p>사용자만 {@code ActiveUserReader} 로 바꾸지 않았다. 세 갈래가 같은 모양으로
     * 존재만 보는 자리라, 하나만 다른 방법을 쓰면 무엇이 다른지 읽는 사람이 찾아봐야 한다.
     */
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
