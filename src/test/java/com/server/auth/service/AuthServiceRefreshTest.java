package com.server.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.server.auth.config.AuthProperties;
import com.server.auth.dto.AuthTokenResponse;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.user.domain.AuthProvider;
import com.server.user.domain.User;
import com.server.user.domain.UserRole;
import com.server.user.repository.UserRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리프레시 회전과 재사용 탐지.
 *
 * <p>여기가 뚫리면 훔친 리프레시 토큰으로 계정을 무기한 점유할 수 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("토큰 갱신")
class AuthServiceRefreshTest {

    @Autowired
    private UserRepository userRepository;

    private RefreshTokenStore refreshTokenStore;
    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(
                new AuthProperties.Google(List.of("client"), null, null),
                new AuthProperties.Jwt("test-secret-key-long-enough-for-hmac-0123456789",
                        "busantour", Duration.ofMinutes(30), Duration.ofDays(14)));

        refreshTokenStore = new RefreshTokenStore(new InMemoryStringRedisTemplate(), properties);
        authService = new AuthService(
                null,
                null,
                new AccessTokenProvider(properties),
                refreshTokenStore,
                userRepository);

        user = userRepository.save(User.ofOAuth(
                AuthProvider.GOOGLE, "sub-1", "a@example.com", "동준", null, UserRole.USER));
    }

    private String issueRefreshToken() {
        return RefreshTokenStore.format(user.getId(), refreshTokenStore.issue(user.getId()));
    }

    @Test
    @DisplayName("갱신하면 새 액세스 토큰과 새 리프레시 토큰을 준다")
    void rotatesRefreshToken() {
        String first = issueRefreshToken();

        AuthTokenResponse response = authService.refresh(first);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotEqualTo(first);
        assertThat(response.user().id()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("이미 쓴 리프레시 토큰을 다시 보내면 거절한다")
    void rejectsReusedRefreshToken() {
        String first = issueRefreshToken();
        authService.refresh(first);

        assertThatThrownBy(() -> authService.refresh(first))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("재사용이 감지되면 그 사용자의 다른 기기 로그인도 끊는다")
    void revokesEveryTokenWhenReuseDetected() {
        // 훔친 쪽과 원래 사용자가 번갈아 갱신하면 반드시 한쪽이 이미 쓴 토큰을 내민다.
        // 그 순간 전부 끊어야 훔친 세션이 살아남지 못한다.
        String stolen = issueRefreshToken();
        String otherDevice = issueRefreshToken();
        authService.refresh(stolen);

        assertThatThrownBy(() -> authService.refresh(stolen))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> authService.refresh(otherDevice))
                .as("재사용 탐지 시 다른 기기의 토큰도 함께 폐기돼야 한다")
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("로그아웃하면 그 기기의 토큰만 폐기한다")
    void logoutRevokesOnlyOneDevice() {
        String phone = issueRefreshToken();
        String laptop = issueRefreshToken();

        authService.logout(phone);

        assertThatThrownBy(() -> authService.refresh(phone))
                .isInstanceOf(BusinessException.class);
        assertThat(authService.refresh(laptop).accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("만료·로그아웃된 토큰은 다른 기기를 끊지 않는다")
    void doesNotRevokeEverythingForMerelyInvalidToken() {
        // 저장소에 없다는 것만으로 탈취로 보면, 로그아웃 직후 클라이언트가 한 번 재시도하는
        // 것만으로 다른 기기가 전부 끊긴다. 회전으로 소비된 흔적이 있을 때만 탈취로 본다.
        String loggedOut = issueRefreshToken();
        String laptop = issueRefreshToken();
        authService.logout(loggedOut);

        assertThatThrownBy(() -> authService.refresh(loggedOut))
                .isInstanceOf(BusinessException.class);

        assertThat(authService.refresh(laptop).accessToken())
                .as("로그아웃된 토큰 재시도가 다른 기기를 끊으면 안 된다")
                .isNotBlank();
    }

    @Test
    @DisplayName("형식이 어긋난 리프레시 토큰을 거절한다")
    void rejectsMalformedRefreshToken() {
        assertThatThrownBy(() -> authService.refresh("garbage"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("탈퇴한 사용자의 토큰으로는 갱신할 수 없다")
    void rejectsRefreshForWithdrawnUser() {
        String token = issueRefreshToken();
        user.delete(java.time.LocalDateTime.now());
        userRepository.saveAndFlush(user);

        assertThatThrownBy(() -> authService.refresh(token))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }
}
