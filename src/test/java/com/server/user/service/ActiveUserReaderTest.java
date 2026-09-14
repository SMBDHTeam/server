package com.server.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
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
 * 같은 확인이 팔로우·차단·프로필에 흩어져 있어 한 곳으로 모았다. 조건이 늘 때 한 군데만
 * 고치면 되도록 하려는 것이라, 규칙 자체를 여기서 고정한다.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("살아 있는 사용자 확인")
class ActiveUserReaderTest {

    @Autowired
    private ActiveUserReader activeUserReader;

    @Autowired
    private UserRepository userRepository;

    private Long activeId;
    private Long withdrawnId;

    @BeforeEach
    void setUp() {
        activeId = userRepository.save(new User("살아있는사람" + System.nanoTime(), null)).getId();

        User withdrawn = new User("탈퇴한사람" + System.nanoTime(), null);
        ReflectionTestUtils.setField(withdrawn, "deletedAt", LocalDateTime.now());
        withdrawnId = userRepository.save(withdrawn).getId();
    }

    @Test
    @DisplayName("살아 있으면 읽어 온다")
    void returnsActiveUser() {
        assertThat(activeUserReader.require(activeId).getId()).isEqualTo(activeId);
        assertThatCode(() -> activeUserReader.requireExists(activeId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("탈퇴한 사용자는 두 방법 모두 거절한다")
    void rejectsWithdrawnUser() {
        assertThatThrownBy(() -> activeUserReader.require(withdrawnId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        assertThatThrownBy(() -> activeUserReader.requireExists(withdrawnId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 사용자도 거절한다")
    void rejectsUnknownUser() {
        assertThatThrownBy(() -> activeUserReader.requireExists(999_999_999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }
}
