package com.server.post.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.post.domain.Post;
import com.server.user.domain.User;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인기 피드 정렬 정책을 고정한다. 점수 계산식({@code 좋아요 + 댓글×2})과 기간(7일)을 바꾸면
 * 이 테스트가 깨져야 한다. 정렬과 기간 조건이 모두 JPQL 안에 있어 실제 쿼리로 확인한다.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("인기 피드")
class PopularFeedTest {

    private static final int POPULAR_FEED_DAYS = 7;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private com.server.hashtag.service.HashtagService hashtagService;

    @Autowired
    private EntityManager entityManager;

    private User author;

    @BeforeEach
    void setUp() {
        author = new User("작성자", null);
        entityManager.persist(author);
    }

    @Test
    @DisplayName("댓글은 좋아요보다 두 배로 쳐서 점수가 높은 순으로 준다")
    void ordersByScore() {
        Post manyLikes = givenPost("좋아요 5", 5, 0, LocalDateTime.now());
        Post manyComments = givenPost("댓글 3", 1, 3, LocalDateTime.now());
        Post quiet = givenPost("조용한 글", 1, 0, LocalDateTime.now());
        flush();

        List<Post> popular = findPopular();

        // 댓글 3개(7점)가 좋아요 5개(5점)를 앞선다.
        assertThat(popular)
                .extracting(Post::getId)
                .containsExactly(manyComments.getId(), manyLikes.getId(), quiet.getId());
    }

    @Test
    @DisplayName("기간을 벗어난 게시물은 점수가 높아도 빠진다")
    void excludesOldPosts() {
        Post old = givenPost(
                "예전 인기글", 999, 999, LocalDateTime.now().minusDays(POPULAR_FEED_DAYS + 1));
        Post recent = givenPost("최근 글", 0, 0, LocalDateTime.now());
        flush();

        assertThat(findPopular())
                .extracting(Post::getId)
                .containsExactly(recent.getId())
                .doesNotContain(old.getId());
    }

    @Test
    @DisplayName("카테고리를 주면 그것이 붙은 게시물만 남는다")
    void filtersByHashtag() {
        Post tagged = givenPost("국밥 맛있다", 1, 0, LocalDateTime.now());
        Post other = givenPost("야경 좋다", 99, 99, LocalDateTime.now());
        flush();
        hashtagService.attach(entityManager.find(Post.class, tagged.getId()), java.util.List.of("맛집"));
        hashtagService.attach(entityManager.find(Post.class, other.getId()), java.util.List.of("야경"));
        flush();

        // 카테고리 탭은 최신 피드와 인기 피드에 같은 기준으로 걸려야 한다.
        assertThat(findPopular("맛집"))
                .extracting(Post::getId)
                .containsExactly(tagged.getId());
    }

    @Test
    @DisplayName("삭제된 게시물은 점수와 무관하게 빠진다")
    void excludesDeletedPosts() {
        Post deleted = givenPost("지운 인기글", 100, 100, LocalDateTime.now());
        deleted.delete();
        Post alive = givenPost("살아있는 글", 0, 0, LocalDateTime.now());
        flush();

        assertThat(findPopular())
                .extracting(Post::getId)
                .containsExactly(alive.getId());
    }

    private List<Post> findPopular() {
        return findPopular(null);
    }

    private List<Post> findPopular(String hashtag) {
        return postRepository.findPopularFeed(
                LocalDateTime.now().minusDays(POPULAR_FEED_DAYS), null, null, hashtag, null,
                PageRequest.of(0, 20));
    }

    /** 집계 컬럼과 작성 시각은 서비스가 아니라 DB 갱신·시계로 정해지므로 직접 심는다. */
    private Post givenPost(String content, int likeCount, int commentCount, LocalDateTime createdAt) {
        Post post = new Post(author, content);
        ReflectionTestUtils.setField(post, "likeCount", likeCount);
        ReflectionTestUtils.setField(post, "commentCount", commentCount);
        ReflectionTestUtils.setField(post, "createdAt", createdAt);
        ReflectionTestUtils.setField(post, "updatedAt", createdAt);
        entityManager.persist(post);
        return post;
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }
}
