package com.server.wishlist.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.common.support.Paging;
import com.server.place.repository.PlaceRepository;
import com.server.user.repository.UserRepository;
import com.server.wishlist.dto.PlaceWishlistListResponse;
import com.server.wishlist.dto.PlaceWishlistResponse;
import com.server.wishlist.dto.PlaceWishlistToggleResponse;
import com.server.wishlist.repository.PlaceWishlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 나중에 가 보려고 담아 둔 장소를 다룬다.
 *
 * <p>담아 둔 장소는 일정을 만들 때 {@code mustVisitPlaceIds} 로 넘어간다. 그 계약이 이미
 * 내부 {@code places.id} 를 받으므로, 여기서 돌려주는 {@code placeId} 를 그대로 쓰면 된다.
 */
@Service
public class PlaceWishlistService {

    private final PlaceWishlistRepository wishlistRepository;
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;

    public PlaceWishlistService(
            PlaceWishlistRepository wishlistRepository,
            PlaceRepository placeRepository,
            UserRepository userRepository
    ) {
        this.wishlistRepository = wishlistRepository;
        this.placeRepository = placeRepository;
        this.userRepository = userRepository;
    }

    /** 이미 담은 장소를 다시 담아도 중복 생성되지 않는다. */
    @Transactional
    public PlaceWishlistToggleResponse add(Long placeId, Long userId) {
        requirePlace(placeId);
        requireUser(userId);
        wishlistRepository.insertIfAbsent(userId, placeId);
        return new PlaceWishlistToggleResponse(true);
    }

    /** 담지 않은 장소를 빼도 오류가 아니다. 화면의 하트를 두 번 눌러도 같은 결과여야 한다. */
    @Transactional
    public PlaceWishlistToggleResponse remove(Long placeId, Long userId) {
        requirePlace(placeId);
        requireUser(userId);
        wishlistRepository.deleteByUserIdAndPlaceId(userId, placeId);
        return new PlaceWishlistToggleResponse(false);
    }

    @Transactional(readOnly = true)
    public PlaceWishlistListResponse getMyWishlist(Long userId, Integer page, Integer size) {
        requireUser(userId);
        return new PlaceWishlistListResponse(
                wishlistRepository.findViewsByUserId(userId, Paging.of(page, size)).stream()
                        .map(PlaceWishlistResponse::from)
                        .toList());
    }

    /**
     * 가려 둔 장소도 담을 수 있게 둔다. 담긴 뒤에 관리자가 가릴 수도 있어 어차피 목록
     * 조회에서 걸러야 하고, 두 곳에서 막으면 같은 규칙이 흩어진다.
     */
    private void requirePlace(Long placeId) {
        if (!placeRepository.existsById(placeId)) {
            throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
        }
    }

    private void requireUser(Long userId) {
        if (!userRepository.existsByIdAndDeletedAtIsNull(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }
}
