package com.server.report.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.post.repository.CommentRepository;
import com.server.post.repository.PostRepository;
import com.server.report.domain.Report;
import com.server.report.domain.ReportTargetType;
import com.server.report.dto.ReportCheckResponse;
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

        Long ownerId = requireTargetOwner(request.targetType(), request.targetId());
        // 자기 글 신고는 관리자 대기열만 채운다. 화면에서 버튼을 숨겨도 API 로는 부를 수 있다.
        if (reporterId.equals(ownerId)) {
            throw new BusinessException(ErrorCode.CANNOT_REPORT_OWN_TARGET);
        }
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                reporterId, request.targetType(), request.targetId())) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }

        // 공백뿐인 설명은 비운다. 그대로 두면 관리자 화면에서 설명이 있는 신고와 구분되지 않는다.
        String reason = request.reason() == null || request.reason().isBlank()
                ? null
                : request.reason().trim();

        try {
            Report report = reportRepository.saveAndFlush(new Report(
                    reporter, request.targetType(), request.targetId(), request.reasonType(), reason));
            return ReportResponse.from(report);
        } catch (DataIntegrityViolationException exception) {
            // 위 확인과 저장 사이에 같은 신고가 들어오면 uk_reports_reporter_target 에 걸린다.
            // 확인만으로는 막을 수 없는 경합이라 제약에 맡기고 같은 응답으로 돌려준다.
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }
    }

    /**
     * 요청자가 이 대상을 이미 신고했는지.
     *
     * <p>신고는 대상마다 한 번뿐이라, 화면이 사유를 고르게 한 뒤에야 409 로 알려주면 같은 과정을
     * 헛되게 반복한다. 대상의 존재는 확인하지 않는다. 지워진 대상도 신고 기록은 남아 있다.
     */
    @Transactional(readOnly = true)
    public ReportCheckResponse check(Long reporterId, ReportTargetType targetType, Long targetId) {
        return new ReportCheckResponse(
                reportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, targetType, targetId));
    }

    /**
     * 대상이 있는지 확인하고 대상의 주인 ID 를 돌려준다. 게시물·댓글은 작성자, 사용자는 본인이다.
     *
     * <p>{@code target_id}에 외래키가 없으므로 대상이 실제로 있는지 여기서 확인한다.
     *
     * <p>사용자도 {@code ActiveUserReader} 대신 저장소로 읽는다. 세 갈래가 같은 모양으로 대상을
     * 읽는 자리라, 하나만 다른 방법을 쓰면 무엇이 다른지 읽는 사람이 찾아봐야 한다.
     */
    private Long requireTargetOwner(ReportTargetType targetType, Long targetId) {
        return switch (targetType) {
            case POST -> postRepository.findByIdAndDeletedAtIsNull(targetId)
                    .map(post -> post.getUser().getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
            case COMMENT -> commentRepository.findByIdAndDeletedAtIsNull(targetId)
                    .map(comment -> comment.getUser().getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
            case USER -> userRepository.findByIdAndDeletedAtIsNull(targetId)
                    .map(User::getId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        };
    }
}
