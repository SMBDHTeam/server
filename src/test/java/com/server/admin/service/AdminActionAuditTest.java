package com.server.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.server.admin.domain.AdminActionTargetType;
import com.server.admin.domain.AdminActionType;
import com.server.admin.repository.AdminActionRepository;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.place.domain.Place;
import com.server.place.repository.PlaceRepository;
import com.server.post.domain.Post;
import com.server.post.repository.PostRepository;
import com.server.post.service.PostService;
import com.server.user.domain.AuthProvider;
import com.server.user.domain.User;
import com.server.user.domain.UserRole;
import com.server.user.repository.UserRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 조치 기록과 삭제 보호.
 *
 * <p>기록이 없으면 &ldquo;누가 언제 무엇을 왜 했는가&rdquo;에 답할 수 없고,
 * 관리자 삭제를 작성자가 되살릴 수 있으면 삭제가 제재로 성립하지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("관리자 조치 기록과 삭제 보호")
class AdminActionAuditTest {

    @Autowired
    private AdminUserService adminUserService;
    @Autowired
    private AdminPlaceService adminPlaceService;
    @Autowired
    private AdminReportService adminReportService;
    @Autowired
    private AdminActionQueryService adminActionQueryService;
    @Autowired
    private PostService postService;
    @Autowired
    private AdminActionRepository adminActionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private PlaceRepository placeRepository;

    private User admin;
    private User author;

    @BeforeEach
    void setUp() {
        admin = save("감사관리자", UserRole.ADMIN, "audit-admin", "audit-admin@example.com");
        author = save("작성자", UserRole.USER, "audit-author", "audit-author@example.com");
    }

    private User save(String nickname, UserRole role, String sub, String email) {
        return userRepository.saveAndFlush(
                User.ofOAuth(AuthProvider.GOOGLE, sub, email, nickname, null, role));
    }

    private Post savePost() {
        return postRepository.saveAndFlush(new Post(author, "감사 테스트 게시물"));
    }

    // ---------- 기록 ----------

    @Test
    @DisplayName("정지하면 사유와 함께 기록이 남는다")
    void recordsSuspension() {
        adminUserService.updateStatus(author.getId(), true, 7, "광고성 반복", admin.getId());

        var actions = adminActionRepository.findAll();
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).getAction()).isEqualTo(AdminActionType.USER_SUSPENDED);
        assertThat(actions.get(0).getAdminId()).isEqualTo(admin.getId());
        assertThat(actions.get(0).getTargetId()).isEqualTo(author.getId());
        assertThat(actions.get(0).getReason()).isEqualTo("광고성 반복");
        // 이력 화면에 그대로 보이는 문구다. 초·나노초가 새어 나오면 안 된다.
        assertThat(actions.get(0).getDetail()).matches("만료 \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");
    }

    @Test
    @DisplayName("정지 해제도 별도 기록으로 남는다")
    void recordsRelease() {
        // 조치를 되돌려도 원래 기록을 지우지 않는다. 지울 수 있으면 기록이 아니다.
        adminUserService.updateStatus(author.getId(), true, 7, "광고성 반복", admin.getId());
        adminUserService.updateStatus(author.getId(), false, null, null, admin.getId());

        assertThat(adminActionRepository.findAll())
                .extracting(a -> a.getAction())
                .containsExactly(
                        AdminActionType.USER_SUSPENDED, AdminActionType.USER_SUSPENSION_RELEASED);
    }

    @Test
    @DisplayName("장소를 가리면 사유와 이름이 남는다")
    void recordsPlaceHidden() {
        Place place = placeRepository.saveAndFlush(new Place(
                "TOUR_API", "audit-place", "12", "감사테스트장소", "관광지",
                "부산광역시 해운대구", new BigDecimal("129.16"), new BigDecimal("35.15"), null));

        adminPlaceService.updateHidden(place.getId(), true, "좌표 오류", admin.getId());

        var action = adminActionRepository.findAll().get(0);
        assertThat(action.getAction()).isEqualTo(AdminActionType.PLACE_HIDDEN);
        assertThat(action.getReason()).isEqualTo("좌표 오류");
        assertThat(action.getDetail()).isEqualTo("감사테스트장소");
    }

    @Test
    @DisplayName("관리자 ID 없이는 조치를 기록하지 않는다")
    void refusesUnknownAdmin() {
        // 주체를 모르는 조치를 남기면 이력 전체가 거짓이 된다. 조용히 넘기지 않는다.
        assertThatThrownBy(() ->
                adminUserService.updateStatus(author.getId(), true, 7, "사유", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("대상으로 이력을 되짚을 수 있다")
    void queriesByTarget() {
        adminUserService.updateStatus(author.getId(), true, 7, "1차", admin.getId());
        adminUserService.updateStatus(author.getId(), false, null, null, admin.getId());

        var result = adminActionQueryService.getActions(
                AdminActionTargetType.USER, author.getId(), 0, 20);

        assertThat(result.totalCount()).isEqualTo(2);
        // 최근 순이다.
        assertThat(result.items().get(0).action())
                .isEqualTo(AdminActionType.USER_SUSPENSION_RELEASED.name());
        assertThat(result.items().get(0).admin().nickname()).isEqualTo("감사관리자");
    }

    @Test
    @DisplayName("종류만 주면 그 종류의 조치 전부를 거른다")
    void filtersByTargetTypeAlone() {
        // 부분 조건을 조용히 버리면 걸렀다고 믿는데 전체가 나온다.
        Place place = placeRepository.saveAndFlush(new Place(
                "TOUR_API", "audit-filter", "12", "필터테스트장소", "관광지",
                "부산광역시 수영구", new BigDecimal("129.11"), new BigDecimal("35.15"), null));
        adminUserService.updateStatus(author.getId(), true, 7, "사유", admin.getId());
        adminPlaceService.updateHidden(place.getId(), true, "좌표 오류", admin.getId());

        var places = adminActionQueryService.getActions(
                AdminActionTargetType.PLACE, null, 0, 20);

        assertThat(places.totalCount()).isEqualTo(1);
        assertThat(places.items().get(0).action())
                .isEqualTo(AdminActionType.PLACE_HIDDEN.name());
    }

    // ---------- 삭제 보호 ----------

    @Test
    @DisplayName("관리자가 지운 글은 작성자가 복구할 수 없다")
    void authorCannotRestoreAdminDeletedPost() {
        Post post = savePost();

        adminReportService.deletePost(post.getId(), admin.getId());

        assertThatThrownBy(() -> postService.restore(post.getId(), author.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_DELETED_BY_ADMIN);
    }

    @Test
    @DisplayName("본인이 지운 글은 그대로 복구된다")
    void authorCanStillRestoreOwnDeletion() {
        // 보호가 본인 삭제까지 막으면 기존 기능이 죽는다.
        Post post = savePost();
        postService.delete(post.getId(), author.getId());

        assertThat(postService.restore(post.getId(), author.getId()).id()).isEqualTo(post.getId());
    }

    @Test
    @DisplayName("관리자 삭제는 '내가 지운 게시물' 목록에 뜨지 않는다")
    void adminDeletedPostIsNotListedAsMine() {
        // 목록에 뜨면 복구를 눌렀다가 403 을 받는다. 애초에 본인이 지운 것이 아니다.
        Post mine = savePost();
        Post removed = savePost();
        postService.delete(mine.getId(), author.getId());
        adminReportService.deletePost(removed.getId(), admin.getId());

        assertThat(postService.getMyDeletedPosts(author.getId(), 0, 20).items())
                .extracting(item -> item.id())
                .containsExactly(mine.getId());
    }

    @Test
    @DisplayName("게시물 삭제도 기록으로 남는다")
    void recordsPostDeletion() {
        Post post = savePost();

        adminReportService.deletePost(post.getId(), admin.getId());

        var action = adminActionRepository.findAll().get(0);
        assertThat(action.getAction()).isEqualTo(AdminActionType.POST_DELETED);
        assertThat(action.getTargetType()).isEqualTo(AdminActionTargetType.POST);
        assertThat(action.getTargetId()).isEqualTo(post.getId());
    }

    // ---------- 역할 부여 ----------

    @Test
    @DisplayName("역할을 바꾸면 이전 값과 함께 기록이 남는다")
    void recordsRoleChange() {
        adminUserService.changeRole(author.getId(), UserRole.ADMIN, admin.getId());

        var action = adminActionRepository.findAll().get(0);
        assertThat(action.getAction()).isEqualTo(AdminActionType.USER_ROLE_CHANGED);
        assertThat(action.getDetail()).isEqualTo("USER -> ADMIN");
        assertThat(userRepository.findById(author.getId()).orElseThrow().getRole())
                .isEqualTo(UserRole.ADMIN);
    }
}
