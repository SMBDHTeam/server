package com.server.user.repository;

import com.server.user.domain.AuthProvider;
import com.server.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    Optional<User> findByNicknameAndDeletedAtIsNull(String nickname);

    boolean existsByNicknameAndDeletedAtIsNull(String nickname);

    /** 소셜 로그인 식별. 이메일이 아니라 제공자가 준 고유 ID 로 찾는다. */
    Optional<User> findByProviderAndProviderIdAndDeletedAtIsNull(
            AuthProvider provider, String providerId);
}
