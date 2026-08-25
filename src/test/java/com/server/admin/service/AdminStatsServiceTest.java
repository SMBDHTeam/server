package com.server.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.admin.dto.StatsMetric;
import com.server.post.domain.Post;
import com.server.post.repository.PostRepository;
import com.server.user.domain.AuthProvider;
import com.server.user.domain.User;
import com.server.user.domain.UserRole;
import com.server.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("관리자 통계")
class AdminStatsServiceTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    @Autowired
    private AdminStatsService adminStatsService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User author;

    @BeforeEach
    void setUp() {
        author = userRepository.saveAndFlush(User.ofOAuth(
                AuthProvider.GOOGLE, "stats-sub", "stats@example.com", "통계작성자", null,
                UserRole.USER));
    }

    private Post savePost(String content) {
        return postRepository.saveAndFlush(new Post(author, content));
    }

    /** 과거 날짜의 게시물을 만든다. created_at 은 엔티티가 정하므로 직접 옮긴다. */
    private void backdate(Long postId, int daysAgo) {
        jdbcTemplate.update("update posts set created_at = ? where id = ?",
                LocalDateTime.now().minusDays(daysAgo), postId);
    }

    @Test
    @DisplayName("총계와 기간 증가분을 함께 준다")
    void reportsTotalAndRecent() {
        savePost("최근 글");
        backdate(savePost("오래된 글").getId(), 60);

        var summary = adminStatsService.getSummary(7);

        assertThat(summary.days()).isEqualTo(7);
        assertThat(summary.posts().total()).isGreaterThanOrEqualTo(2);
        assertThat(summary.posts().recent()).isGreaterThanOrEqualTo(1);
        assertThat(summary.posts().recent()).isLessThan(summary.posts().total());
    }

    @Test
    @DisplayName("삭제된 게시물은 총계에서 뺀다")
    void excludesDeletedPosts() {
        Post post = savePost("지울 글");
        long before = adminStatsService.getSummary(7).posts().total();

        post.delete();
        postRepository.saveAndFlush(post);

        assertThat(adminStatsService.getSummary(7).posts().total()).isEqualTo(before - 1);
    }

    @Test
    @DisplayName("추이는 값이 없는 날도 0으로 채운다")
    void fillsEmptyDaysWithZero() {
        // 빈 날을 건너뛰면 화면이 그래프를 실제보다 완만하게 그린다.
        savePost("오늘 글");

        var trend = adminStatsService.getTrend(StatsMetric.POSTS, 7);

        assertThat(trend.points()).hasSize(7);
        assertThat(trend.points()).extracting(p -> p.date()).doesNotHaveDuplicates();
        assertThat(trend.points().get(6).date()).isEqualTo(LocalDate.now(KOREA_ZONE));
    }

    @Test
    @DisplayName("추이가 날짜 순으로 이어진다")
    void trendIsContinuous() {
        var trend = adminStatsService.getTrend(StatsMetric.POSTS, 5);

        for (int i = 1; i < trend.points().size(); i++) {
            assertThat(trend.points().get(i).date())
                    .isEqualTo(trend.points().get(i - 1).date().plusDays(1));
        }
    }

    @Test
    @DisplayName("오늘 만든 게시물이 오늘 칸에 잡힌다")
    void countsTodayPost() {
        long before = adminStatsService.getTrend(StatsMetric.POSTS, 1).points().get(0).count();

        savePost("오늘 글");

        assertThat(adminStatsService.getTrend(StatsMetric.POSTS, 1).points().get(0).count())
                .isEqualTo(before + 1);
    }

    @Test
    @DisplayName("기간을 생략하면 7일이고 상한을 넘기면 잘린다")
    void clampsDays() {
        assertThat(adminStatsService.getSummary(null).days()).isEqualTo(7);
        assertThat(adminStatsService.getSummary(0).days()).isEqualTo(7);
        assertThat(adminStatsService.getTrend(StatsMetric.POSTS, 9999).points()).hasSize(365);
    }

    @Test
    @DisplayName("정지된 사용자 수를 센다")
    void countsSuspendedUsers() {
        long before = adminStatsService.getSummary(7).suspendedUsers();
        author.suspend(LocalDateTime.now().plusDays(7), "광고성");
        userRepository.saveAndFlush(author);

        assertThat(adminStatsService.getSummary(7).suspendedUsers()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("인기 해시태그는 삭제된 게시물의 몫을 세지 않는다")
    void excludesDeletedPostsFromPopularHashtags() {
        // hashtags.post_count 를 그대로 쓰면 삭제된 게시물의 몫이 남아 순위가 뒤틀린다.
        var popular = adminStatsService.getPopularHashtags(10);

        assertThat(popular.type()).isEqualTo("HASHTAG");
        assertThat(popular.items()).allSatisfy(item -> assertThat(item.count()).isPositive());
    }

    @Test
    @DisplayName("인기 장소는 가려진 장소를 제외한다")
    void excludesHiddenPlacesFromPopular() {
        var popular = adminStatsService.getPopularPlaces(10);

        assertThat(popular.type()).isEqualTo("PLACE");
        assertThat(popular.items()).allSatisfy(item -> assertThat(item.id()).isNotNull());
    }
}
