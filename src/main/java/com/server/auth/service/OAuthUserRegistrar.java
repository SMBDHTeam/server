package com.server.auth.service;

import com.server.user.domain.AuthProvider;
import com.server.user.domain.User;
import com.server.user.domain.UserRole;
import com.server.user.repository.UserRepository;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검증을 통과한 구글 계정을 우리 사용자로 만든다.
 *
 * <p>식별자는 구글이 준 {@code sub} 다. 이메일은 바뀔 수 있고 해지 후 재사용될 수도 있어
 * 계정을 잇는 기준으로 쓰지 않는다.
 *
 * <p><b>권한은 여기서 정하지 않는다.</b> 로그인으로 만들어지는 사용자는 항상 일반 사용자이며,
 * 관리자 지정은 DB 에서 한다({@code UPDATE users SET role='ADMIN' WHERE id=?}).
 * 로그인이 권한을 덮어쓰면 DB 로 준 권한이 다음 로그인에 사라진다.
 */
@Component
public class OAuthUserRegistrar {

    private static final Logger log = LoggerFactory.getLogger(OAuthUserRegistrar.class);

    private static final int NICKNAME_MAX_LENGTH = 20;
    private static final String FALLBACK_NICKNAME = "여행자";

    private final UserRepository userRepository;

    public OAuthUserRegistrar(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User register(GoogleIdentity identity) {
        return userRepository
                .findByProviderAndProviderIdAndDeletedAtIsNull(AuthProvider.GOOGLE, identity.subject())
                .map(existing -> updateExisting(existing, identity))
                .orElseGet(() -> createNew(identity));
    }

    /** 이미 있는 사용자를 로그인 시점 정보로 맞춘다. 권한은 건드리지 않는다. */
    private User updateExisting(User user, GoogleIdentity identity) {
        user.syncFromProvider(identity.email(), identity.pictureUrl());
        return user;
    }

    private User createNew(GoogleIdentity identity) {
        User user = User.ofOAuth(
                AuthProvider.GOOGLE,
                identity.subject(),
                identity.email(),
                uniqueNickname(identity.name()),
                identity.pictureUrl(),
                UserRole.USER);
        return userRepository.save(user);
    }

    /**
     * 닉네임은 살아 있는 사용자 사이에서 고유해야 한다({@code uk_users_nickname_active}).
     * 구글 표시 이름은 흔히 겹치므로 충돌하면 숫자를 붙인다.
     *
     * <p>여기서 확보하지 않으면 첫 로그인이 고유 제약 위반으로 실패한다. 사용자 입장에서는
     * 이름이 겹쳤을 뿐인데 가입이 안 되는 것으로 보인다.
     */
    private String uniqueNickname(String preferred) {
        String base = normalizeNickname(preferred);
        if (!userRepository.existsByNicknameAndDeletedAtIsNull(base)) {
            return base;
        }
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = base + ThreadLocalRandom.current().nextInt(1000, 10000);
            if (!userRepository.existsByNicknameAndDeletedAtIsNull(candidate)) {
                return candidate;
            }
        }
        // 열 번 모두 겹치는 것은 사실상 없지만, 가입이 막히는 것보다는 긴 이름이 낫다.
        return base + System.nanoTime();
    }

    private String normalizeNickname(String preferred) {
        if (preferred == null || preferred.isBlank()) {
            return FALLBACK_NICKNAME;
        }
        String trimmed = preferred.trim();
        return trimmed.length() <= NICKNAME_MAX_LENGTH
                ? trimmed
                : trimmed.substring(0, NICKNAME_MAX_LENGTH);
    }
}
