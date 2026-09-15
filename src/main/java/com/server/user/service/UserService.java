package com.server.user.service;

import com.server.common.support.Paging;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.follow.dto.FollowUserResponse;
import com.server.block.repository.BlockRepository;
import com.server.follow.repository.FollowRepository;
import com.server.post.repository.PostRepository;
import com.server.user.domain.User;
import com.server.user.dto.NicknameUpdateRequest;
import com.server.user.dto.ProfileImageUpdateRequest;
import com.server.user.dto.UserProfileResponse;
import com.server.user.dto.UserSearchListResponse;
import com.server.user.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final FollowRepository followRepository;
    private final BlockRepository blockRepository;
    private final ActiveUserReader activeUserReader;

    public UserService(
            UserRepository userRepository,
            PostRepository postRepository,
            FollowRepository followRepository,
            BlockRepository blockRepository,
            ActiveUserReader activeUserReader
    ) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.followRepository = followRepository;
        this.blockRepository = blockRepository;
        this.activeUserReader = activeUserReader;
    }

    /** 닉네임에 검색어가 포함된 사용자를 찾는다. 검색어가 비어 있으면 빈 목록을 준다. */
    @Transactional(readOnly = true)
    public UserSearchListResponse search(String keyword, Integer page, Integer size) {
        if (keyword == null || keyword.isBlank()) {
            return new UserSearchListResponse(List.of());
        }
        List<FollowUserResponse> items = userRepository
                .findByNicknameContainingIgnoreCaseAndDeletedAtIsNullOrderByNicknameAsc(
                        keyword.trim(), Paging.of(page, size))
                .stream()
                .map(FollowUserResponse::from)
                .toList();
        return new UserSearchListResponse(items);
    }

    @Transactional
    public UserProfileResponse changeProfileImage(Long userId, ProfileImageUpdateRequest request) {
        activeUserReader.require(userId).changeProfileImage(request.profileImageUrl());
        return getProfile(userId, userId);
    }

    @Transactional
    public UserProfileResponse removeProfileImage(Long userId) {
        activeUserReader.require(userId).removeProfileImage();
        return getProfile(userId, userId);
    }

    /** 이미 쓰는 사람이 있는 닉네임이면 거절한다. 자기 닉네임을 그대로 보내는 건 허용한다. */
    @Transactional
    public UserProfileResponse changeNickname(Long userId, NicknameUpdateRequest request) {
        User user = activeUserReader.require(userId);

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

        boolean otherUser = requesterId != null && !requesterId.equals(userId);
        boolean following = otherUser
                && followRepository.existsByFollowerIdAndFollowingId(requesterId, userId);
        // 차단 여부를 주지 않으면 화면이 차단·해제 버튼 중 무엇을 보여야 할지 알 수 없다.
        // 누른 직후에는 응답으로 알지만, 프로필을 다시 열면 그 기억이 사라진다.
        boolean blocked = otherUser
                && blockRepository.existsByBlockerIdAndBlockedId(requesterId, userId);

        return UserProfileResponse.of(
                user,
                postRepository.countByUserIdAndDeletedAtIsNull(userId),
                followRepository.countByFollowingId(userId),
                followRepository.countByFollowerId(userId),
                following,
                blocked,
                userId.equals(requesterId));
    }

}
