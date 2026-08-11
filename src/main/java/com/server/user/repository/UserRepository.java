package com.server.user.repository;

import com.server.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    Optional<User> findByNicknameAndDeletedAtIsNull(String nickname);

    boolean existsByNicknameAndDeletedAtIsNull(String nickname);
}
