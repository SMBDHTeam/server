package com.server.user.repository;

import com.server.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByIdAndDeletedAtIsNull(Long id);

    Optional<User> findByNicknameAndDeletedAtIsNull(String nickname);

    boolean existsByNicknameAndDeletedAtIsNull(String nickname);

    /** 닉네임 부분 일치 검색. 탈퇴한 사용자는 제외한다. */
    List<User> findByNicknameContainingIgnoreCaseAndDeletedAtIsNullOrderByNicknameAsc(
            String keyword, Pageable pageable);
}
