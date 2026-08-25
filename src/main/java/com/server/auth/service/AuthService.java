package com.server.auth.service;

import com.server.auth.dto.AuthTokenResponse;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final OAuthUserRegistrar oAuthUserRegistrar;
    private final AccessTokenProvider accessTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final UserRepository userRepository;

    public AuthService(
            GoogleIdTokenVerifier googleIdTokenVerifier,
            OAuthUserRegistrar oAuthUserRegistrar,
            AccessTokenProvider accessTokenProvider,
            RefreshTokenStore refreshTokenStore,
            UserRepository userRepository
    ) {
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.oAuthUserRegistrar = oAuthUserRegistrar;
        this.accessTokenProvider = accessTokenProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.userRepository = userRepository;
    }

    @Transactional
    public AuthTokenResponse loginWithGoogle(String idToken) {
        GoogleIdentity identity = googleIdTokenVerifier.verify(idToken);
        User user = oAuthUserRegistrar.register(identity);
        return issueFor(user);
    }

    /**
     * 리프레시 토큰을 회전시킨다.
     *
     * <p>쓴 토큰을 지우고 새 토큰을 준다. 이미 지워진 토큰이 다시 오면 탈취로 보고 그
     * 사용자의 리프레시를 전부 폐기한다. 훔친 쪽과 원래 사용자가 번갈아 갱신하면 반드시
     * 한쪽이 지워진 토큰을 내밀게 되므로 탈취가 드러난다.
     */
    @Transactional
    public AuthTokenResponse refresh(String refreshToken) {
        RefreshTokenStore.RefreshTokenRef ref = RefreshTokenStore.parse(refreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        if (!refreshTokenStore.consume(ref.userId(), ref.token())) {
            // 저장소에 없다. 이미 회전으로 소비된 토큰이면 탈취로 본다. 훔친 쪽과 원래
            // 사용자가 번갈아 갱신하면 반드시 한쪽이 소비된 토큰을 내밀게 된다.
            if (refreshTokenStore.wasRotated(ref.userId(), ref.token())) {
                log.warn("Refresh token reuse detected. Revoking all sessions. userId={}",
                        ref.userId());
                refreshTokenStore.revokeAll(ref.userId());
            } else {
                // 만료·로그아웃·조작이다. 이 요청만 거절하면 되고 다른 기기를 끊을 근거는 없다.
                log.debug("Refresh token not active. userId={}", ref.userId());
            }
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        User user = userRepository.findByIdAndDeletedAtIsNull(ref.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return issueFor(user);
    }

    /** 이 기기의 리프레시만 지운다. 다른 기기의 로그인은 유지한다. */
    public void logout(String refreshToken) {
        // 회전 흔적을 남기지 않는다. 로그아웃한 토큰이 다시 오는 것은 클라이언트 재시도일 뿐이라
        // 탈취로 보고 다른 기기까지 끊으면 안 된다.
        RefreshTokenStore.parse(refreshToken)
                .ifPresent(ref -> refreshTokenStore.consume(ref.userId(), ref.token(), false));
    }

    private AuthTokenResponse issueFor(User user) {
        String refreshToken = refreshTokenStore.issue(user.getId());
        return new AuthTokenResponse(
                accessTokenProvider.issue(user),
                accessTokenProvider.accessTtl().toSeconds(),
                RefreshTokenStore.format(user.getId(), refreshToken),
                new AuthTokenResponse.AuthUser(
                        user.getId(),
                        user.getNickname(),
                        user.getProfileImageUrl(),
                        user.getRole().name()));
    }
}
