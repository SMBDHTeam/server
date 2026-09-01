package com.server.hashtag.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.hashtag.repository.HashtagRepository;
import com.server.post.domain.Post;
import com.server.user.domain.User;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 같은 게시물을 두 번 저장하거나 복구를 두 번 눌러도 태그 연결이 깨지지 않아야 한다.
 * 기본키 충돌은 실제 DB 로만 확인할 수 있어 통합 테스트로 둔다.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("해시태그 연결 중복")
class HashtagAttachConflictTest {

    @Autowired
    private HashtagService hashtagService;

    @Autowired
    private HashtagRepository hashtagRepository;

    @Autowired
    private EntityManager entityManager;

    private Post post;

    @BeforeEach
    void setUp() {
        User author = new User("작성자", null);
        entityManager.persist(author);
        post = new Post(author, "광안리 야경 #야경 #카페");
        entityManager.persist(post);
        entityManager.flush();
    }

    @Test
    @DisplayName("같은 본문으로 두 번 연결해도 실패하지 않는다")
    void attachTwiceDoesNotFail() {
        hashtagService.attach(post, List.of("야경", "카페"));

        // 두 번째 호출이 기본키 충돌로 터지면 복구 버튼을 두 번 누른 사용자가 500 을 받는다.
        assertThat(hashtagService.attach(post, List.of("야경", "카페")))
                .containsExactly("야경", "카페");
    }

    @Test
    @DisplayName("두 번 연결해도 태그 사용 수는 한 번만 오른다")
    void attachTwiceCountsOnce() {
        hashtagService.attach(post, List.of("야경", "카페"));
        entityManager.flush();
        long afterFirst = postCountOf("야경");

        hashtagService.attach(post, List.of("야경", "카페"));
        entityManager.flush();

        assertThat(postCountOf("야경")).isEqualTo(afterFirst);
    }

    private long postCountOf(String name) {
        return hashtagRepository.findByNameIn(List.of(name)).get(0).getPostCount();
    }
}
