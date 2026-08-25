package com.server.block.service;

import com.server.block.dto.BlockResponse;
import com.server.block.dto.BlockUserListResponse;
import com.server.block.repository.BlockRepository;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.follow.dto.FollowUserResponse;
import com.server.follow.repository.FollowRepository;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final BlockRepository blockRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    public BlockService(
            BlockRepository blockRepository,
            FollowRepository followRepository,
            UserRepository userRepository
    ) {
        this.blockRepository = blockRepository;
        this.followRepository = followRepository;
        this.userRepository = userRepository;
    }

    /**
     * 차단하면 서로의 팔로우를 끊는다. 차단한 상대의 소식을 계속 받는 것은 앞뒤가 맞지 않는다.
     * 이미 차단한 상대를 다시 차단해도 관계가 중복 생성되지 않는다.
     */
    @Transactional
    public BlockResponse block(Long targetUserId, Long userId) {
        if (targetUserId.equals(userId)) {
            throw new BusinessException(ErrorCode.INVALID_BLOCK_REQUEST);
        }
        findActiveUser(targetUserId);
        findActiveUser(userId);

        blockRepository.insertIfAbsent(userId, targetUserId);
        followRepository.deleteByFollowerIdAndFollowingId(userId, targetUserId);
        followRepository.deleteByFollowerIdAndFollowingId(targetUserId, userId);

        return new BlockResponse(true);
    }

    /** 차단을 풀어도 끊긴 팔로우는 되살리지 않는다. */
    @Transactional
    public BlockResponse unblock(Long targetUserId, Long userId) {
        findActiveUser(targetUserId);
        blockRepository.deleteByBlockerIdAndBlockedId(userId, targetUserId);
        return new BlockResponse(false);
    }

    @Transactional(readOnly = true)
    public BlockUserListResponse getMyBlocks(Long userId, Integer page, Integer size) {
        findActiveUser(userId);
        List<FollowUserResponse> items = blockRepository
                .findByBlockerIdOrderByCreatedAtDesc(userId, pageRequest(page, size)).stream()
                .map(block -> FollowUserResponse.from(block.getBlocked()))
                .toList();
        return new BlockUserListResponse(items);
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
