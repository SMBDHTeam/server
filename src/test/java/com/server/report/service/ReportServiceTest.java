package com.server.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.post.domain.Comment;
import com.server.post.domain.Post;
import com.server.post.repository.CommentRepository;
import com.server.post.repository.PostRepository;
import com.server.report.domain.Report;
import com.server.report.domain.ReportReasonType;
import com.server.report.domain.ReportTargetType;
import com.server.report.dto.ReportCreateRequest;
import com.server.report.dto.ReportResponse;
import com.server.report.repository.ReportRepository;
import com.server.user.domain.AuthProvider;
import com.server.user.domain.User;
import com.server.user.domain.UserRole;
import com.server.user.repository.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("신고 접수")
class ReportServiceTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Autowired
    private ReportService reportService;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private UserRepository userRepository;

    private User reporter;
    private User author;
    private Post post;

    @BeforeEach
    void setUp() {
        reporter = save("신고자", "reporter-sub");
        author = save("작성자", "author-sub");
        post = postRepository.saveAndFlush(new Post(author, "광안리 야경 보러 갔어요"));
    }

    private User save(String nickname, String sub) {
        return userRepository.saveAndFlush(User.ofOAuth(
                AuthProvider.GOOGLE, sub, sub + "@example.com", nickname, null, UserRole.USER));
    }

    private ReportCreateRequest request(ReportReasonType type, String reason) {
        return new ReportCreateRequest(ReportTargetType.POST, post.getId(), type, reason);
    }

    @Test
    @DisplayName("사유 유형과 설명을 함께 저장한다")
    void savesReasonTypeAndDetail() {
        ReportResponse response = reportService.report(
                reporter.getId(), request(ReportReasonType.SPAM, "같은 링크를 반복해서 올림"));

        Report saved = reportRepository.findById(response.id()).orElseThrow();
        assertThat(response.reasonType()).isEqualTo(ReportReasonType.SPAM);
        assertThat(saved.getReasonType()).isEqualTo(ReportReasonType.SPAM);
        assertThat(saved.getReason()).isEqualTo("같은 링크를 반복해서 올림");
    }

    @Test
    @DisplayName("설명 없이 유형만으로 신고할 수 있다")
    void acceptsReasonTypeWithoutDetail() {
        ReportResponse response = reportService.report(
                reporter.getId(), request(ReportReasonType.ABUSE, null));

        assertThat(reportRepository.findById(response.id()).orElseThrow().getReason()).isNull();
    }

    @Test
    @DisplayName("공백뿐인 설명은 비워서 저장한다")
    void storesBlankDetailAsNull() {
        // 공백을 그대로 두면 관리자 화면에 빈 사유 칸이 생기고, 설명이 있는 신고와 구분되지 않는다.
        ReportResponse response = reportService.report(
                reporter.getId(), request(ReportReasonType.SPAM, "   "));

        assertThat(reportRepository.findById(response.id()).orElseThrow().getReason()).isNull();
    }

    @Test
    @DisplayName("같은 대상은 사유 유형이 달라도 다시 신고할 수 없다")
    void rejectsDuplicateEvenWithDifferentType() {
        reportService.report(reporter.getId(), request(ReportReasonType.SPAM, null));

        assertThatThrownBy(() -> reportService.report(
                reporter.getId(), request(ReportReasonType.ABUSE, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_REPORTED);
    }

    @Test
    @DisplayName("없는 게시물은 신고할 수 없다")
    void rejectsMissingTarget() {
        ReportCreateRequest missing = new ReportCreateRequest(
                ReportTargetType.POST, post.getId() + 9999, ReportReasonType.SPAM, null);

        assertThatThrownBy(() -> reportService.report(reporter.getId(), missing))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("기타는 설명이 있어야 하고, 나머지 유형은 없어도 된다")
    void requiresDetailOnlyForOther() {
        assertThat(VALIDATOR.validate(request(ReportReasonType.OTHER, null))).isNotEmpty();
        assertThat(VALIDATOR.validate(request(ReportReasonType.OTHER, "  "))).isNotEmpty();
        assertThat(VALIDATOR.validate(request(ReportReasonType.OTHER, "사진이 도용됨"))).isEmpty();
        assertThat(VALIDATOR.validate(request(ReportReasonType.SPAM, null))).isEmpty();
    }

    @Test
    @DisplayName("사유 유형이 없거나 설명이 500자를 넘으면 받지 않는다")
    void rejectsMissingTypeAndLongDetail() {
        assertThat(VALIDATOR.validate(request(null, "광고"))).isNotEmpty();
        assertThat(VALIDATOR.validate(request(ReportReasonType.SPAM, "가".repeat(501)))).isNotEmpty();
    }

    @Test
    @DisplayName("자기 게시물은 신고할 수 없다")
    void rejectsOwnPost() {
        assertThatThrownBy(() -> reportService.report(
                author.getId(), request(ReportReasonType.SPAM, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CANNOT_REPORT_OWN_TARGET);
        assertThat(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                author.getId(), ReportTargetType.POST, post.getId())).isFalse();
    }

    @Test
    @DisplayName("자기 댓글은 신고할 수 없고, 남의 댓글은 신고할 수 있다")
    void rejectsOwnCommentOnly() {
        Comment comment = commentRepository.saveAndFlush(new Comment(post, author, null, "야경 명소 추천해요"));
        ReportCreateRequest onComment = new ReportCreateRequest(
                ReportTargetType.COMMENT, comment.getId(), ReportReasonType.ABUSE, null);

        assertThatThrownBy(() -> reportService.report(author.getId(), onComment))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CANNOT_REPORT_OWN_TARGET);

        ReportResponse response = reportService.report(reporter.getId(), onComment);
        assertThat(response.targetType()).isEqualTo(ReportTargetType.COMMENT);
    }

    @Test
    @DisplayName("자기 자신은 신고할 수 없다")
    void rejectsSelf() {
        ReportCreateRequest onSelf = new ReportCreateRequest(
                ReportTargetType.USER, reporter.getId(), ReportReasonType.OTHER, "테스트");

        assertThatThrownBy(() -> reportService.report(reporter.getId(), onSelf))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CANNOT_REPORT_OWN_TARGET);
    }

    @Test
    @DisplayName("신고 여부는 신고 전 false, 신고 후 true 다")
    void checksWhetherAlreadyReported() {
        assertThat(reportService.check(reporter.getId(), ReportTargetType.POST, post.getId()).reported()).isFalse();

        reportService.report(reporter.getId(), request(ReportReasonType.SPAM, null));

        assertThat(reportService.check(reporter.getId(), ReportTargetType.POST, post.getId()).reported()).isTrue();
        // 다른 사람의 신고는 내 신고 여부에 섞이지 않는다.
        assertThat(reportService.check(author.getId(), ReportTargetType.POST, post.getId()).reported()).isFalse();
    }
}
