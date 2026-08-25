package com.server.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.post.domain.Comment;
import com.server.post.domain.Post;
import com.server.post.dto.CommentUpdateRequest;
import com.server.post.repository.CommentRepository;
import com.server.post.repository.PostRepository;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 수정 응답은 메모리에 있는 엔티티를 그대로 돌려주므로, 트랜잭션이 없어 DB 에 반영되지
 * 않아도 성공한 것처럼 보인다. 저장 여부는 다시 읽어서 확인해야 의미가 있다.
 *
 * <p>이 테스트에 {@code @Transactional} 을 붙이면 안 된다. 붙이면 서비스가 테스트의
 * 트랜잭션에 얹혀 가 변경 감지가 동작하므로, 서비스에 트랜잭션이 없어도 통과한다.
 * 그래서 뒷정리를 직접 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("댓글 수정 저장")
class CommentUpdatePersistenceTest {

    @Autowired
    private CommentService commentService;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    private Long postId;
    private Long commentId;
    private Long authorId;

    @BeforeEach
    void setUp() {
        User author = userRepository.save(new User("수정저장테스트작성자", null));
        Post post = postRepository.save(new Post(author, "본문"));
        Comment comment = commentRepository.save(new Comment(post, author, null, "원래 내용"));

        authorId = author.getId();
        postId = post.getId();
        commentId = comment.getId();
    }

    @AfterEach
    void tearDown() {
        // 다른 테스트의 목록 조회에 섞이지 않도록 만든 행을 되돌린다.
        commentRepository.deleteById(commentId);
        postRepository.deleteById(postId);
        userRepository.deleteById(authorId);
    }

    @Test
    @DisplayName("수정한 내용이 DB 에 반영된다")
    void updatePersists() {
        commentService.update(postId, commentId, authorId, new CommentUpdateRequest("고친 내용"));

        assertThat(commentRepository.findById(commentId))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.getContent()).isEqualTo("고친 내용");
                    assertThat(saved.getUpdatedAt()).isAfter(saved.getCreatedAt());
                });
    }
}
