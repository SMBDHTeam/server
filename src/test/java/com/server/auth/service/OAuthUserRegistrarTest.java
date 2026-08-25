package com.server.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.user.domain.AuthProvider;
import com.server.user.domain.User;
import com.server.user.domain.UserRole;
import com.server.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("구글 계정 등록")
class OAuthUserRegistrarTest {

    @Autowired
    private UserRepository userRepository;

    private OAuthUserRegistrar registrar() {
        return new OAuthUserRegistrar(userRepository);
    }

    private GoogleIdentity identity(String subject, String email, String name) {
        return new GoogleIdentity(subject, email, name, "https://example.com/p.png");
    }

    @Test
    @DisplayName("처음 로그인하면 사용자를 만든다")
    void createsUserOnFirstLogin() {
        User user = registrar().register(identity("sub-1", "a@example.com", "동준"));

        assertThat(user.getId()).isNotNull();
        assertThat(user.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(user.getProviderId()).isEqualTo("sub-1");
        assertThat(user.getEmail()).isEqualTo("a@example.com");
        assertThat(user.getNickname()).isEqualTo("동준");
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("같은 sub 로 다시 로그인하면 사용자를 새로 만들지 않는다")
    void reusesUserOnSecondLogin() {
        User first = registrar().register(identity("sub-1", "a@example.com", "동준"));
        User second = registrar().register(identity("sub-1", "a@example.com", "동준"));

        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("이메일이 바뀌어도 sub 가 같으면 같은 사용자다")
    void tracksUserBySubjectNotEmail() {
        // 이메일은 바뀔 수 있어 계정을 잇는 기준으로 쓰지 않는다.
        User first = registrar().register(identity("sub-1", "old@example.com", "동준"));
        User second = registrar().register(identity("sub-1", "new@example.com", "동준"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    @DisplayName("사용자가 바꾼 닉네임을 구글 이름으로 되돌리지 않는다")
    void keepsUserChosenNickname() {
        User user = registrar().register(identity("sub-1", "a@example.com", "구글이름"));
        user.changeNickname("내가정한닉");

        User again = registrar().register(identity("sub-1", "a@example.com", "구글이름"));

        assertThat(again.getNickname()).isEqualTo("내가정한닉");
    }

    @Test
    @DisplayName("닉네임이 겹치면 다른 값을 만들어 가입시킨다")
    void avoidsNicknameCollision() {
        // 확보하지 않으면 첫 로그인이 uk_users_nickname_active 위반으로 실패한다.
        // 사용자에게는 이름이 겹쳤을 뿐인데 가입이 안 되는 것으로 보인다.
        registrar().register(identity("sub-1", "a@example.com", "동준"));
        User second = registrar().register(identity("sub-2", "b@example.com", "동준"));

        assertThat(second.getNickname()).isNotEqualTo("동준").startsWith("동준");
        assertThat(second.getId()).isNotNull();
    }

    @Test
    @DisplayName("이름이 없으면 기본 닉네임을 쓴다")
    void fallsBackWhenNameIsMissing() {
        User user = registrar().register(identity("sub-1", "a@example.com", null));

        assertThat(user.getNickname()).isNotBlank();
    }

    @Test
    @DisplayName("로그인은 DB 로 준 관리자 권한을 건드리지 않는다")
    void loginDoesNotOverwriteRole() {
        // 관리자 지정은 DB 에서 한다. 로그인이 권한을 다시 계산하면 UPDATE 로 준 권한이
        // 다음 로그인에 사라져, DB 로 관리자를 만드는 방법 자체가 성립하지 않는다.
        User user = registrar().register(identity("sub-1", "a@example.com", "동준"));
        user.changeRole(UserRole.ADMIN);
        userRepository.saveAndFlush(user);

        User after = registrar().register(identity("sub-1", "a@example.com", "동준"));

        assertThat(after.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("탈퇴한 계정으로 다시 로그인하면 새 사용자로 만든다")
    void createsNewUserAfterWithdrawal() {
        User first = registrar().register(identity("sub-1", "a@example.com", "동준"));
        first.delete(LocalDateTime.now());
        userRepository.saveAndFlush(first);

        User second = registrar().register(identity("sub-1", "a@example.com", "동준"));

        assertThat(second.getId()).isNotEqualTo(first.getId());
    }
}
