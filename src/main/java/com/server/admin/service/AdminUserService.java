package com.server.admin.service;

import com.server.admin.domain.AdminActionTargetType;
import com.server.admin.domain.AdminActionType;
import com.server.admin.dto.AdminUserDetailResponse;
import com.server.admin.dto.AdminUserListResponse;
import com.server.admin.dto.AdminUserResponse;
import com.server.auth.service.RefreshTokenStore;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.common.support.ServerClock;
import com.server.post.repository.PostRepository;
import com.server.report.repository.ReportRepository;
import com.server.user.domain.User;
import com.server.user.domain.UserRole;
import com.server.user.domain.UserStatus;
import com.server.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 사용자 관리.
 *
 * <p>정지는 쓰기만 막고 읽기는 허용한다. 읽기까지 막으면 정지된 사용자가 자기 상태를
 * 확인할 방법이 없다.
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    /** 이력 화면에 그대로 보이는 문구다. 초와 나노초까지 나오면 읽기 어렵다. */
    private static final DateTimeFormatter UNTIL_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final ReportRepository reportRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final AdminActionRecorder adminActionRecorder;

    public AdminUserService(
            UserRepository userRepository,
            PostRepository postRepository,
            ReportRepository reportRepository,
            RefreshTokenStore refreshTokenStore,
            AdminActionRecorder adminActionRecorder
    ) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.reportRepository = reportRepository;
        this.refreshTokenStore = refreshTokenStore;
        this.adminActionRecorder = adminActionRecorder;
    }

    @Transactional(readOnly = true)
    public AdminUserListResponse getUsers(
            String keyword, UserStatus status, UserRole role, Integer page, Integer size) {
        int resolvedPage = page == null || page < 0 ? 0 : page;
        int resolvedSize = size == null || size <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(size, MAX_PAGE_SIZE);
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();

        List<User> users = userRepository.searchForAdmin(
                normalized, status, role, PageRequest.of(resolvedPage, resolvedSize));

        return new AdminUserListResponse(
                users.stream().map(AdminUserResponse::from).toList(),
                userRepository.countForAdmin(normalized, status, role));
    }

    /**
     * 역할을 바꾼다.
     *
     * <p>이 API 가 없어 지금까지 운영 DB 에 직접 붙어 {@code UPDATE users SET role} 을
     * 실행해야 했다. 사람이 운영 DB 에 접속하는 일을 정기 업무로 만들면 안 된다.
     *
     * <p>역할은 액세스 토큰에 담기므로 바꾼 즉시 반영되지 않는다. 리프레시 토큰을 폐기해
     * 재로그인을 강제한다. 특히 강등에서 중요하다 &mdash; 폐기하지 않으면 최대 30분간
     * 관리자 권한이 남는다.
     */
    @Transactional
    public AdminUserResponse changeRole(Long userId, UserRole role, Long adminId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        UserRole before = user.getRole();
        // 마지막 관리자가 내려가면 관리자 화면에 들어올 사람이 없고, 되돌리려면 DB 를 직접 고쳐야 한다.
        if (before == UserRole.ADMIN && role != UserRole.ADMIN
                && userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN) <= 1) {
            throw new BusinessException(ErrorCode.CANNOT_DEMOTE_LAST_ADMIN);
        }
        user.changeRole(role);
        log.info("User role changed. userId={}, from={}, to={}, adminId={}",
                userId, before, role, adminId);
        adminActionRecorder.record(adminId, AdminActionType.USER_ROLE_CHANGED,
                AdminActionTargetType.USER, userId, null, before + " -> " + role);

        revokeTokensQuietly(userId);
        return AdminUserResponse.from(user);
    }

    /** 탈퇴한 사용자도 조회한다. 신고를 따라 들어왔을 때 이미 탈퇴했다는 사실이 필요하다. */
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return new AdminUserDetailResponse(
                AdminUserResponse.from(user),
                postRepository.countByUserIdAndDeletedAtIsNull(userId),
                reportRepository.countByReporterId(userId),
                reportRepository.countAgainstUser(userId));
    }

    /**
     * 정지하거나 해제한다.
     *
     * <p>정지하면 그 사용자의 리프레시 토큰을 모두 지운다. 액세스 토큰은 무상태라 남은
     * 수명(기본 30분) 동안 유효하지만, 갱신을 막으면 그 뒤로는 이어갈 수 없다. 즉시
     * 끊으려면 액세스 토큰까지 저장해야 하는데, 그러면 모든 요청이 저장소를 거친다.
     *
     * <p>관리자는 정지할 수 없다. 관리자끼리 서로 정지시키면 아무도 풀 수 없는 상태가
     * 될 수 있고, 그때 남는 수단은 DB 를 직접 고치는 것뿐이다.
     */
    @Transactional
    public AdminUserResponse updateStatus(
            Long userId, boolean suspended, Integer days, String reason, Long adminId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (suspended && user.isAdmin()) {
            throw new BusinessException(ErrorCode.CANNOT_SUSPEND_ADMIN);
        }

        if (suspended) {
            LocalDateTime until = days == null ? null : ServerClock.now().plusDays(days);
            user.suspend(until, reason);
            log.info("User suspended. userId={}, until={}", userId, until);
            adminActionRecorder.record(adminId, AdminActionType.USER_SUSPENDED,
                    AdminActionTargetType.USER, userId, reason,
                    until == null ? "기한 없음" : "만료 " + until.format(UNTIL_FORMAT));
            revokeTokensQuietly(userId);
        } else {
            user.releaseSuspension();
            log.info("User suspension released. userId={}", userId);
            adminActionRecorder.record(adminId, AdminActionType.USER_SUSPENSION_RELEASED,
                    AdminActionTargetType.USER, userId);
        }
        return AdminUserResponse.from(user);
    }

    /**
     * 리프레시 토큰 폐기는 실패해도 정지를 되돌리지 않는다.
     *
     * <p>Redis 가 죽었다고 악성 사용자를 정지시키지 못하면 곤란하다. 중요한 것은 DB 의
     * 정지 상태이며, 쓰기 차단은 그것만으로 동작한다. 게다가 Redis 가 없으면 갱신 자체가
     * 실패하므로 폐기하지 못한 토큰으로도 세션을 이어갈 수 없다.
     */
    private void revokeTokensQuietly(Long userId) {
        try {
            long revoked = refreshTokenStore.revokeAll(userId);
            log.info("Revoked refresh tokens for suspended user. userId={}, count={}",
                    userId, revoked);
        } catch (RuntimeException exception) {
            log.error("Failed to revoke refresh tokens for suspended user. userId={}",
                    userId, exception);
        }
    }
}
