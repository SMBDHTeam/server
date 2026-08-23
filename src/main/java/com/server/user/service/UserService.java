package com.server.user.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.follow.repository.FollowRepository;
import com.server.post.repository.PostRepository;
import com.server.user.domain.User;
import com.server.user.dto.UserProfileResponse;
import com.server.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final FollowRepository followRepository;

    public UserService(
            UserRepository userRepository,
            PostRepository postRepository,
            FollowRepository followRepository
    ) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.followRepository = followRepository;
    }

    /**
     * @param requesterId 조회하는 사용자. 인증이 없어 전달되지 않을 수 있으며,
     *                    없으면 팔로우 여부를 판단하지 않는다.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId, Long requesterId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        boolean following = requesterId != null
                && !requesterId.equals(userId)
                && followRepository.existsByFollowerIdAndFollowingId(requesterId, userId);

        return UserProfileResponse.of(
                user,
                postRepository.countByUserIdAndDeletedAtIsNull(userId),
                followRepository.countByFollowingId(userId),
                followRepository.countByFollowerId(userId),
                following,
                userId.equals(requesterId));
    }
}
