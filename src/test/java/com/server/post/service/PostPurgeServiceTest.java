package com.server.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.bookmark.repository.BookmarkRepository;
import com.server.post.domain.Comment;
import com.server.post.domain.Post;
import com.server.post.domain.PostMedia;
import com.server.post.domain.MediaType;
import com.server.post.repository.CommentLikeRepository;
import com.server.post.repository.CommentRepository;
import com.server.post.repository.PostLikeRepository;
import com.server.post.repository.PostMediaRepository;
import com.server.post.repository.PostRepository;
import com.server.user.domain.User;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시물을 참조하는 행이 여럿이라 지우는 순서를 틀리면 외래키에 걸린다. 실제 DB 로
 * 확인해야 의미가 있어 통합 테스트로 둔다.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("기한이 지난 게시물 정리")
class PostPurgeServiceTest {

    private static final int RETENTION_DAYS = 30;

    @Autowired
    private PostPurgeService postPurgeService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostMediaRepository postMediaRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private CommentLikeRepository commentLikeRepository;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private EntityManager entityManager;

    private User author;
    private User reader;

    @BeforeEach
    void setUp() {
        author = new User("작성자", null);
        reader = new User("독자", null);
        entityManager.persist(author);
        entityManager.persist(reader);
        entityManager.flush();
    }

    @Test
    @DisplayName("기한이 지난 게시물은 딸린 데이터까지 지운다")
    void purgesExpiredPostWithDependents() {
        Post expired = givenPost(LocalDateTime.now().minusDays(RETENTION_DAYS + 1));
        Long postId = expired.getId();
        Long commentId = givenCommentWithLike(expired);
        entityManager.flush();
        entityManager.clear();

        assertThat(postPurgeService.purgeExpired(RETENTION_DAYS)).isEqualTo(1);
        entityManager.flush();
        entityManager.clear();

        assertThat(postRepository.findById(postId)).isEmpty();
        assertThat(postMediaRepository.findByPostId(postId)).isEmpty();
        assertThat(commentRepository.findById(commentId)).isEmpty();
        assertThat(commentLikeRepository.existsByCommentIdAndUserId(commentId, reader.getId()))
                .isFalse();
        assertThat(postLikeRepository.existsByPostIdAndUserId(postId, reader.getId())).isFalse();
        assertThat(bookmarkRepository.existsByUserIdAndPostId(reader.getId(), postId)).isFalse();
    }

    @Test
    @DisplayName("아직 복구할 수 있는 게시물은 건드리지 않는다")
    void keepsRestorablePost() {
        Post restorable = givenPost(LocalDateTime.now().minusDays(RETENTION_DAYS - 1));
        entityManager.flush();
        entityManager.clear();

        assertThat(postPurgeService.purgeExpired(RETENTION_DAYS)).isZero();
        assertThat(postRepository.findById(restorable.getId())).isPresent();
    }

    @Test
    @DisplayName("지우지 않은 게시물은 아무리 오래돼도 건드리지 않는다")
    void keepsLivePost() {
        Post live = new Post(author, "살아있는 글");
        ReflectionTestUtils.setField(live, "createdAt", LocalDateTime.now().minusYears(2));
        entityManager.persist(live);
        entityManager.flush();
        entityManager.clear();

        assertThat(postPurgeService.purgeExpired(RETENTION_DAYS)).isZero();
        assertThat(postRepository.findById(live.getId())).isPresent();
    }

    private Post givenPost(LocalDateTime deletedAt) {
        Post post = new Post(author, "지운 글");
        ReflectionTestUtils.setField(post, "deletedAt", deletedAt);
        entityManager.persist(post);
        entityManager.persist(new PostMedia(post, MediaType.IMAGE, "https://e.com/a.jpg", 0));
        entityManager.flush();

        postLikeRepository.insertIfAbsent(post.getId(), reader.getId());
        bookmarkRepository.insertIfAbsent(reader.getId(), post.getId());
        return post;
    }

    private Long givenCommentWithLike(Post post) {
        Comment comment = new Comment(post, reader, null, "댓글");
        entityManager.persist(comment);
        entityManager.flush();
        commentLikeRepository.insertIfAbsent(comment.getId(), reader.getId());
        return comment.getId();
    }
}
