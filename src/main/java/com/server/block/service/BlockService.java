package com.server.block.service;

import com.server.common.support.Paging;
import com.server.block.dto.BlockResponse;
import com.server.block.dto.BlockUserListResponse;
import com.server.block.repository.BlockRepository;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.follow.dto.FollowUserResponse;
import com.server.follow.repository.FollowRepository;
import com.server.user.domain.User;
import com.server.user.service.ActiveUserReader;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockService {

    private final BlockRepository blockRepository;
    private final FollowRepository followRepository;
    private final ActiveUserReader activeUserReader;

    public BlockService(
            BlockRepository blockRepository,
            FollowRepository followRepository,
            ActiveUserReader activeUserReader
    ) {
        this.blockRepository = blockRepository;
        this.followRepository = followRepository;
        this.activeUserReader = activeUserReader;
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
        activeUserReader.requireExists(targetUserId);
        activeUserReader.requireExists(userId);

        blockRepository.insertIfAbsent(userId, targetUserId);
        followRepository.deleteByFollowerIdAndFollowingId(userId, targetUserId);
        followRepository.deleteByFollowerIdAndFollowingId(targetUserId, userId);

        // 방금 끊었으므로 언제나 false 다. 화면이 팔로우 버튼을 되돌리는 데 쓴다.
        return new BlockResponse(true, false);
    }

    /** 차단을 풀어도 끊긴 팔로우는 되살리지 않는다. */
    @Transactional
    public BlockResponse unblock(Long targetUserId, Long userId) {
        activeUserReader.requireExists(targetUserId);
        blockRepository.deleteByBlockerIdAndBlockedId(userId, targetUserId);
        // 차단을 풀어도 끊긴 팔로우는 되살리지 않으므로 지금 상태를 다시 읽어 준다.
        return new BlockResponse(false,
                followRepository.existsByFollowerIdAndFollowingId(userId, targetUserId));
    }

    @Transactional(readOnly = true)
    public BlockUserListResponse getMyBlocks(Long userId, Integer page, Integer size) {
        activeUserReader.requireExists(userId);
        List<FollowUserResponse> items = blockRepository
                .findByBlockerIdOrderByCreatedAtDesc(userId, Paging.of(page, size)).stream()
                .map(block -> FollowUserResponse.from(block.getBlocked()))
                .toList();
        return new BlockUserListResponse(items);
    }

}
