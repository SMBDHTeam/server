package com.server.follow.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.follow.domain.Follow;
import com.server.follow.dto.FollowResponse;
import com.server.follow.dto.FollowUserListResponse;
import com.server.follow.dto.FollowUserResponse;
import com.server.follow.repository.FollowRepository;
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
    private final UserRepository userRepository;

    public FollowService(FollowRepository followRepository, UserRepository userRepository) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
    }

    /** 이미 팔로우한 상대를 다시 팔로우해도 관계가 중복 생성되지 않는다. */
    @Transactional
    public FollowResponse follow(Long targetUserId, Long userId) {
        if (targetUserId.equals(userId)) {
            throw new BusinessException(ErrorCode.INVALID_FOLLOW_REQUEST);
        }
        User target = findActiveUser(targetUserId);
        User me = findActiveUser(userId);

        if (!followRepository.existsByFollowerIdAndFollowingId(userId, targetUserId)) {
            followRepository.save(new Follow(me, target));
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
