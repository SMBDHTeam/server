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
 * 태그는 미리 등록해 둔 것만 붙는다. 자유 입력을 허용하면 같은 뜻의 태그가 표기만 달리해
 * 흩어지고, 태그로 거르는 화면이 제 구실을 못 한다.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("등록된 해시태그만 붙는다")
class RegisteredHashtagOnlyTest {

    @Autowired
    private HashtagService hashtagService;

    @Autowired
    private HashtagRepository hashtagRepository;

    @Autowired
    private EntityManager entityManager;

    private Post post;

    @BeforeEach
    void setUp() {
        User author = new User("작성자" + System.nanoTime(), null);
        entityManager.persist(author);
        post = new Post(author, "본문");
        entityManager.persist(post);
        entityManager.flush();
    }

    @Test
    @DisplayName("등록된 태그는 붙는다")
    void attachesRegisteredTag() {
        assertThat(hashtagService.attach(post, List.of("맛집", "카페")))
                .containsExactly("맛집", "카페");
    }

    @Test
    @DisplayName("등록되지 않은 태그는 무시한다")
    void ignoresUnregisteredTag() {
        assertThat(hashtagService.attach(post, List.of("내가만든태그")))
                .isEmpty();
        // 목록에 없는 태그를 썼다고 새로 만들지 않는다.
        assertThat(hashtagRepository.findByNameIn(List.of("내가만든태그"))).isEmpty();
    }

    @Test
    @DisplayName("등록된 것과 아닌 것이 섞이면 등록된 것만 붙는다")
    void keepsOnlyRegistered() {
        // 오타 하나로 글 작성이 막히면 안 되므로 거절하지 않고 그 태그만 버린다.
        assertThat(hashtagService.attach(post, List.of("맛집", "없는태그", "카페")))
                .containsExactly("맛집", "카페");
    }

    @Test
    @DisplayName("교체하면 뺀 카테고리는 복구해도 살아나지 않는다")
    void reattachRemovesLinks() {
        hashtagService.attach(post, List.of("맛집", "카페"));
        entityManager.flush();

        hashtagService.reattach(post, List.of("야경"));
        entityManager.flush();
        entityManager.clear();

        // 연결을 남겨 두면 복구할 때 뺐던 것이 다시 붙는다.
        assertThat(hashtagService.restoreForPost(post.getId())).containsExactly("야경");
    }

    @Test
    @DisplayName("등록되지 않은 태그만 있어도 게시물 저장은 막지 않는다")
    void doesNotFailWhenNothingRegistered() {
        assertThat(hashtagService.attach(post, List.of("없는태그1", "없는태그2"))).isEmpty();
        assertThat(post.getId()).isNotNull();
    }
}
