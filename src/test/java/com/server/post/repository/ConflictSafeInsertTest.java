package com.server.post.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.block.repository.BlockRepository;
import com.server.bookmark.repository.BookmarkRepository;
import com.server.follow.repository.FollowRepository;
import com.server.post.domain.Post;
import com.server.user.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요·저장·팔로우·차단은 같은 요청이 두 번 들어와도 행이 하나만 남아야 한다.
 * 확인 후 저장하는 방식은 동시 요청에서 양쪽 다 없다고 읽어 기본키 충돌이 나므로,
 * 넣어 보고 충돌을 무시하는 방식이 맞는지 실제 DB 로 확인한다.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("중복 삽입 방지")
class ConflictSafeInsertTest {

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private EntityManager entityManager;

    private Long authorId;
    private Long readerId;
    private Long postId;

    @BeforeEach
    void setUp() {
        User author = new User("작성자", null);
        User reader = new User("독자", null);
        entityManager.persist(author);
        entityManager.persist(reader);
        Post post = new Post(author, "본문");
        entityManager.persist(post);
        entityManager.flush();

        authorId = author.getId();
        readerId = reader.getId();
        postId = post.getId();
    }

    @Test
    @DisplayName("좋아요를 두 번 넣어도 두 번째는 아무 행도 넣지 않는다")
    void postLike() {
        assertThat(postLikeRepository.insertIfAbsent(postId, readerId)).isEqualTo(1);
        assertThat(postLikeRepository.insertIfAbsent(postId, readerId)).isZero();
        assertThat(postLikeRepository.existsByPostIdAndUserId(postId, readerId)).isTrue();
    }

    @Test
    @DisplayName("저장을 두 번 넣어도 두 번째는 아무 행도 넣지 않는다")
    void bookmark() {
        assertThat(bookmarkRepository.insertIfAbsent(readerId, postId)).isEqualTo(1);
        assertThat(bookmarkRepository.insertIfAbsent(readerId, postId)).isZero();
    }

    @Test
    @DisplayName("팔로우를 두 번 넣어도 두 번째는 아무 행도 넣지 않는다")
    void follow() {
        assertThat(followRepository.insertIfAbsent(readerId, authorId)).isEqualTo(1);
        assertThat(followRepository.insertIfAbsent(readerId, authorId)).isZero();
        assertThat(followRepository.countByFollowingId(authorId)).isEqualTo(1);
    }

    @Test
    @DisplayName("차단을 두 번 넣어도 두 번째는 아무 행도 넣지 않는다")
    void block() {
        assertThat(blockRepository.insertIfAbsent(readerId, authorId)).isEqualTo(1);
        assertThat(blockRepository.insertIfAbsent(readerId, authorId)).isZero();
        assertThat(blockRepository.existsBetween(authorId, readerId)).isTrue();
    }
}
