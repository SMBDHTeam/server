package com.server.user.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.follow.dto.FollowUserResponse;
import com.server.follow.repository.FollowRepository;
import com.server.post.repository.PostRepository;
import com.server.user.domain.User;
import com.server.user.dto.NicknameUpdateRequest;
import com.server.user.dto.UserProfileResponse;
import com.server.user.dto.UserSearchListResponse;
import com.server.user.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

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

    /** 닉네임에 검색어가 포함된 사용자를 찾는다. 검색어가 비어 있으면 빈 목록을 준다. */
    @Transactional(readOnly = true)
    public UserSearchListResponse search(String keyword, Integer page, Integer size) {
        if (keyword == null || keyword.isBlank()) {
            return new UserSearchListResponse(List.of());
        }
        List<FollowUserResponse> items = userRepository
                .findByNicknameContainingIgnoreCaseAndDeletedAtIsNullOrderByNicknameAsc(
                        keyword.trim(), pageRequest(page, size))
                .stream()
                .map(FollowUserResponse::from)
                .toList();
        return new UserSearchListResponse(items);
    }

    /** 이미 쓰는 사람이 있는 닉네임이면 거절한다. 자기 닉네임을 그대로 보내는 건 허용한다. */
    @Transactional
    public UserProfileResponse changeNickname(Long userId, NicknameUpdateRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!user.getNickname().equals(request.nickname())
                && userRepository.existsByNicknameAndDeletedAtIsNull(request.nickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_USED);
        }
        user.changeNickname(request.nickname());

        return getProfile(userId, userId);
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

    private PageRequest pageRequest(Integer page, Integer size) {
        int resolvedPage = page == null || page < 0 ? 0 : page;
        int resolvedSize = size == null || size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(resolvedPage, resolvedSize);
    }
}
