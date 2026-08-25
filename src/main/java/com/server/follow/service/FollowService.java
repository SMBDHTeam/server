package com.server.follow.service;

import com.server.block.repository.BlockRepository;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.follow.dto.FollowResponse;
import com.server.follow.dto.FollowUserListResponse;
import com.server.follow.dto.FollowUserResponse;
import com.server.follow.repository.FollowRepository;
import com.server.notification.domain.NotificationTargetType;
import com.server.notification.domain.NotificationType;
import com.server.notification.service.NotificationService;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FollowService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final FollowRepository followRepository;
    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public FollowService(
            FollowRepository followRepository,
            BlockRepository blockRepository,
            UserRepository userRepository,
            NotificationService notificationService
    ) {
        this.followRepository = followRepository;
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /**
     * 이미 팔로우한 상대를 다시 팔로우해도 관계가 중복 생성되지 않는다.
     *
     * <p>차단이 있으면 관계를 만들지 않는다. 다만 방향에 따라 응답이 다르다.
     * 내가 차단한 상대라면 차단을 먼저 풀라고 알려주고, 상대가 나를 차단한 경우에는
     * 성공한 것처럼 응답한다. 거절하면 차단당했다는 사실이 그대로 드러나기 때문이다.
     */
    @Transactional
    public FollowResponse follow(Long targetUserId, Long userId) {
        if (targetUserId.equals(userId)) {
            throw new BusinessException(ErrorCode.INVALID_FOLLOW_REQUEST);
        }
        findActiveUser(targetUserId);
        findActiveUser(userId);

        if (blockRepository.existsByBlockerIdAndBlockedId(userId, targetUserId)) {
            throw new BusinessException(ErrorCode.FOLLOW_BLOCKED_USER);
        }
        // 상대가 나를 차단한 경우에는 관계만 만들지 않고 응답은 성공과 똑같이 준다.
        // 이미 팔로우 중이면 관계도 알림도 새로 만들지 않는다.
        if (!blockRepository.existsByBlockerIdAndBlockedId(targetUserId, userId)
                && followRepository.insertIfAbsent(userId, targetUserId) > 0) {
            notificationService.notify(
                    targetUserId,
                    userId,
                    NotificationType.FOLLOW,
                    NotificationTargetType.USER,
                    userId);
        }
        return new FollowResponse(followRepository.countByFollowingId(targetUserId), true);
    }

    @Transactional
    public FollowResponse unfollow(Long targetUserId, Long userId) {
        findActiveUser(targetUserId);
        followRepository.deleteByFollowerIdAndFollowingId(userId, targetUserId);
        return new FollowResponse(followRepository.countByFollowingId(targetUserId), false);
    }

    /** 대상을 팔로우하는 사람들. */
    @Transactional(readOnly = true)
    public FollowUserListResponse getFollowers(Long targetUserId, Integer page, Integer size) {
        findActiveUser(targetUserId);
        List<FollowUserResponse> items = followRepository
                .findByFollowingIdOrderByCreatedAtDesc(targetUserId, pageRequest(page, size)).stream()
                .map(follow -> FollowUserResponse.from(follow.getFollower()))
                .toList();
        return new FollowUserListResponse(items, followRepository.countByFollowingId(targetUserId));
    }

    /** 대상이 팔로우하는 사람들. */
    @Transactional(readOnly = true)
    public FollowUserListResponse getFollowings(Long targetUserId, Integer page, Integer size) {
        findActiveUser(targetUserId);
        List<FollowUserResponse> items = followRepository
                .findByFollowerIdOrderByCreatedAtDesc(targetUserId, pageRequest(page, size)).stream()
                .map(follow -> FollowUserResponse.from(follow.getFollowing()))
                .toList();
        return new FollowUserListResponse(items, followRepository.countByFollowerId(targetUserId));
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private PageRequest pageRequest(Integer page, Integer size) {
        int resolvedPage = page == null || page < 0 ? 0 : page;
        int resolvedSize = size == null || size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(resolvedPage, resolvedSize);
    }
}
