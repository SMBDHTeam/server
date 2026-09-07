package com.server.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.post.domain.Post;
import com.server.post.dto.CommentCreateRequest;
import com.server.post.dto.CommentResponse;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 댓글 작성 응답을 실제 JPA 로 확인한다.
 *
 * <p>댓글 수를 올리는 질의가 {@code clearAutomatically = true} 라 영속성 컨텍스트를 비운다.
 * 그 뒤에 응답을 만들면서 작성자를 읽으므로, 지연 로딩 프록시였다면 여기서 터진다.
 * Mockito 로 짠 테스트는 영속성 컨텍스트가 없어 이 문제를 잡지 못한다.
 *
 * <p>{@code @Transactional} 을 붙이지 않는다. 붙이면 서비스가 테스트의 트랜잭션에 얹혀 가
 * 컨텍스트가 살아 있어, 실제 동작과 다른 조건에서 통과한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("댓글 작성 응답")
class CommentCreatePersistenceTest {

    @Autowired
    private CommentService commentService;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    /** deleteByPostId 는 파생 삭제라 트랜잭션이 있어야 한다. 뒷정리에만 쓴다. */
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long postId;
    private Long authorId;

    @BeforeEach
    void setUp() {
        User author = userRepository.save(new User("작성응답테스트작성자", null));
        Post post = postRepository.save(new Post(author, "본문"));
        authorId = author.getId();
        postId = post.getId();
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            commentRepository.deleteByPostId(postId);
            postRepository.deleteById(postId);
            userRepository.deleteById(authorId);
        });
    }

    @Test
    @DisplayName("컨텍스트가 비워진 뒤에도 작성자와 댓글 수를 담아 돌려준다")
    void returnsAuthorAndCountAfterContextCleared() {
        CommentResponse response = commentService.create(
                postId, authorId, new CommentCreateRequest("첫 댓글", null));

        assertThat(response.author()).isNotNull();
        assertThat(response.author().nickname()).isEqualTo("작성응답테스트작성자");
        assertThat(response.postCommentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("댓글이 늘어난 만큼 수가 올라간다")
    void countsEachComment() {
        commentService.create(postId, authorId, new CommentCreateRequest("하나", null));
        CommentResponse second = commentService.create(
                postId, authorId, new CommentCreateRequest("둘", null));

        assertThat(second.postCommentCount()).isEqualTo(2);
        assertThat(postRepository.findCommentCountById(postId)).isEqualTo(2);
    }
}
