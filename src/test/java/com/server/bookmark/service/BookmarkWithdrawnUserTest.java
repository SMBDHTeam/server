package com.server.bookmark.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.post.domain.Post;
import com.server.post.repository.PostRepository;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
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
 * 저장·저장 취소·목록은 한 자원의 세 동작이므로 요청자 조건이 같아야 한다.
 *
 * <p>취소만 탈퇴한 사용자를 통과시키고 있었다. 지울 행이 없어 겉으로는 같은 결과였지만,
 * 나중에 조건이 바뀔 때 이 한 곳만 빠질 위험이 있어 맞춘다.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("탈퇴한 사용자의 저장 요청")
class BookmarkWithdrawnUserTest {

    @Autowired
    private BookmarkService bookmarkService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    private Long postId;
    private Long withdrawnUserId;

    @BeforeEach
    void setUp() {
        User author = userRepository.save(new User("저장테스트작성자", null));
        postId = postRepository.save(new Post(author, "본문")).getId();

        User withdrawn = userRepository.save(new User("탈퇴한사람", null));
        ReflectionTestUtils.setField(withdrawn, "deletedAt", LocalDateTime.now());
        withdrawnUserId = userRepository.save(withdrawn).getId();
    }

    @Test
    @DisplayName("저장은 거절한다")
    void rejectsBookmark() {
        assertThatThrownBy(() -> bookmarkService.bookmark(postId, withdrawnUserId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("저장 취소도 같은 조건으로 거절한다")
    void rejectsRemove() {
        assertThatThrownBy(() -> bookmarkService.removeBookmark(postId, withdrawnUserId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("목록도 거절한다")
    void rejectsList() {
        assertThatThrownBy(() -> bookmarkService.getMyBookmarks(withdrawnUserId, 0, 20))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }
}
