package com.server.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.server.admin.dto.AdminReportDetailResponse;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.post.domain.Post;
import com.server.post.domain.ReportStatus;
import com.server.post.repository.PostRepository;
import com.server.post.service.PostService;
import com.server.report.domain.Report;
import com.server.report.domain.ReportTargetType;
import com.server.report.repository.ReportRepository;
import com.server.user.domain.AuthProvider;
import com.server.user.domain.User;
import com.server.user.domain.UserRole;
import com.server.user.repository.UserRepository;
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
@DisplayName("관리자 신고 처리")
class AdminReportServiceTest {

    @Autowired
    private AdminReportService adminReportService;
    @Autowired
    private PostService postService;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private UserRepository userRepository;

    private User admin;
    private User author;
    private User reporter;

    @BeforeEach
    void setUp() {
        admin = save("관리자", UserRole.ADMIN, "admin-sub");
        author = save("작성자", UserRole.USER, "author-sub");
        reporter = save("신고자", UserRole.USER, "reporter-sub");
    }

    private User save(String nickname, UserRole role, String sub) {
        return userRepository.saveAndFlush(User.ofOAuth(
                AuthProvider.GOOGLE, sub, sub + "@example.com", nickname, null, role));
    }

    private Post savePost(String content) {
        return postRepository.saveAndFlush(new Post(author, content));
    }

    private Report saveReport(ReportTargetType type, Long targetId) {
        return reportRepository.saveAndFlush(
                new Report(reporter, type, targetId, "광고성입니다"));
    }

    @Test
    @DisplayName("상세에 신고 대상 원본을 함께 준다")
    void includesTargetContentInDetail() {
        // 신고 사유만 보고는 조치할 수 없다. 관리자가 원본을 따로 찾아야 하면 화면이 성립하지 않는다.
        Post post = savePost("광안리 야경 보러 갔어요");
        Report report = saveReport(ReportTargetType.POST, post.getId());

        AdminReportDetailResponse detail = adminReportService.getReport(report.getId());

        assertThat(detail.target()).isNotNull();
        assertThat(detail.target().content()).isEqualTo("광안리 야경 보러 갔어요");
        assertThat(detail.target().author().id()).isEqualTo(author.getId());
        assertThat(detail.target().deleted()).isFalse();
    }

    @Test
    @DisplayName("이미 삭제된 대상도 삭제 표시와 함께 보여준다")
    void marksAlreadyDeletedTarget() {
        // 접수 뒤 작성자가 스스로 지웠거나 다른 관리자가 먼저 조치한 경우다. 감추면
        // 관리자가 사라진 대상을 계속 찾게 된다.
        Post post = savePost("지워질 글");
        Report report = saveReport(ReportTargetType.POST, post.getId());
        postService.deleteByAdmin(post.getId());

        AdminReportDetailResponse detail = adminReportService.getReport(report.getId());

        assertThat(detail.target()).isNotNull();
        assertThat(detail.target().deleted()).isTrue();
    }

    @Test
    @DisplayName("대상 행 자체가 없으면 target 이 null 이다")
    void returnsNullTargetWhenRowIsGone() {
        Report report = saveReport(ReportTargetType.POST, 999999L);

        assertThat(adminReportService.getReport(report.getId()).target()).isNull();
    }

    @Test
    @DisplayName("처리하면 담당자와 시각을 남긴다")
    void recordsHandler() {
        Report report = saveReport(ReportTargetType.POST, savePost("글").getId());

        var updated = adminReportService.updateStatus(
                report.getId(), ReportStatus.RESOLVED, admin.getId());

        assertThat(updated.status()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(updated.handledBy().id()).isEqualTo(admin.getId());
        assertThat(updated.handledAt()).isNotNull();
    }

    @Test
    @DisplayName("종결한 신고를 다시 대기로 되돌릴 수 있다")
    void allowsReopening() {
        // 되돌릴 수 없으면 잘못 종결한 신고를 다루려고 같은 내용의 새 신고를 기다려야 한다.
        Report report = saveReport(ReportTargetType.POST, savePost("글").getId());
        adminReportService.updateStatus(report.getId(), ReportStatus.RESOLVED, admin.getId());

        var reopened = adminReportService.updateStatus(
                report.getId(), ReportStatus.PENDING, admin.getId());

        assertThat(reopened.status()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    @DisplayName("상태로 거르고 전체 건수를 함께 준다")
    void filtersByStatusWithTotalCount() {
        saveReport(ReportTargetType.POST, savePost("글1").getId());
        Report second = saveReport(ReportTargetType.COMMENT, 1L);
        adminReportService.updateStatus(second.getId(), ReportStatus.RESOLVED, admin.getId());

        var pending = adminReportService.getReports(ReportStatus.PENDING, null, 0, 20);

        assertThat(pending.items()).hasSize(1);
        assertThat(pending.totalCount()).isEqualTo(1);
        assertThat(adminReportService.getReports(null, null, 0, 20).totalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("오래된 신고부터 반환한다")
    void returnsOldestFirst() {
        // 최신순이면 방치된 신고가 계속 뒤로 밀린다.
        Report first = saveReport(ReportTargetType.POST, savePost("먼저").getId());
        Report second = saveReport(ReportTargetType.COMMENT, 2L);

        var reports = adminReportService.getReports(null, null, 0, 20);

        assertThat(reports.items().get(0).id()).isEqualTo(first.getId());
        assertThat(reports.items().get(1).id()).isEqualTo(second.getId());
    }

    @Test
    @DisplayName("없는 신고를 처리하려 하면 404 다")
    void rejectsUnknownReport() {
        assertThatThrownBy(() -> adminReportService.updateStatus(
                999999L, ReportStatus.RESOLVED, admin.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPORT_NOT_FOUND);
    }

    @Test
    @DisplayName("관리자 삭제도 소프트 삭제라 복구 기한 안에는 되살릴 수 있다")
    void adminDeleteIsSoftDelete() {
        // 관리자가 지운 게시물만 다른 상태로 남으면 복구·정리 경로가 갈라진다.
        Post post = savePost("관리자가 지울 글");

        postService.deleteByAdmin(post.getId());

        Post found = postRepository.findById(post.getId()).orElseThrow();
        assertThat(found.getDeletedAt()).isNotNull();
        assertThat(postService.restore(post.getId(), author.getId()).id()).isEqualTo(post.getId());
    }
}
