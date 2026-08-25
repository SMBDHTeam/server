package com.server.user.repository;

import com.server.user.domain.AuthProvider;
import com.server.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import com.server.user.domain.UserStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByIdAndDeletedAtIsNull(Long id);

    Optional<User> findByNicknameAndDeletedAtIsNull(String nickname);

    boolean existsByNicknameAndDeletedAtIsNull(String nickname);

    /** 닉네임 부분 일치 검색. 탈퇴한 사용자는 제외한다. */
    List<User> findByNicknameContainingIgnoreCaseAndDeletedAtIsNullOrderByNicknameAsc(
            String keyword, Pageable pageable);

    /** 소셜 로그인 식별. 이메일이 아니라 제공자가 준 고유 ID 로 찾는다. */
    Optional<User> findByProviderAndProviderIdAndDeletedAtIsNull(
            AuthProvider provider, String providerId);

    /**
     * 관리자 사용자 검색. 닉네임·이메일 부분 일치이며 탈퇴한 사용자도 포함한다.
     * 신고를 따라 들어왔을 때 이미 탈퇴했다는 사실 자체가 필요한 정보다.
     */
    @Query("""
            select user from User user
            where (:keyword is null
                   or lower(user.nickname) like lower(concat('%', :keyword, '%'))
                   or lower(user.email) like lower(concat('%', :keyword, '%')))
              and (:status is null or user.status = :status)
            order by user.createdAt desc
            """)
    List<User> searchForAdmin(
            @Param("keyword") String keyword,
            @Param("status") UserStatus status,
            Pageable pageable);

    @Query("""
            select count(user) from User user
            where (:keyword is null
                   or lower(user.nickname) like lower(concat('%', :keyword, '%'))
                   or lower(user.email) like lower(concat('%', :keyword, '%')))
              and (:status is null or user.status = :status)
            """)
    long countForAdmin(@Param("keyword") String keyword, @Param("status") UserStatus status);
}
