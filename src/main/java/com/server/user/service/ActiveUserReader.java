package com.server.user.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
import org.springframework.stereotype.Component;

/**
 * 탈퇴하지 않은 사용자인지 확인한다.
 *
 * <p>같은 세 줄이 팔로우·차단·프로필에 흩어져 있었다. 규칙이 한 곳에 있어야, 나중에
 * "정지된 계정도 막는다" 처럼 조건이 늘 때 한 군데만 고치면 된다.
 *
 * <p>두 가지를 나눠 둔 것은 필요한 만큼만 읽기 위해서다. 존재만 확인하면 되는 곳에서
 * 엔티티를 통째로 읽으면 쓰지도 않을 행을 영속성 컨텍스트에 올린다.
 */
@Component
public class ActiveUserReader {

    private final UserRepository userRepository;

    public ActiveUserReader(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 사용자를 읽어 온다. 내용을 바꾸거나 값을 써야 할 때 쓴다. */
    public User require(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /** 있는지만 본다. 대상이 살아 있는지 확인하고 넘어가는 곳에 쓴다. */
    public void requireExists(Long userId) {
        if (!userRepository.existsByIdAndDeletedAtIsNull(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }
}
