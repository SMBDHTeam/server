package com.server.wishlist.controller;

import com.server.auth.service.AuthenticatedUser;
import com.server.auth.web.LoginUser;
import com.server.wishlist.dto.PlaceWishlistListResponse;
import com.server.wishlist.dto.PlaceWishlistToggleResponse;
import com.server.wishlist.service.PlaceWishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
@Tag(name = "장소 위시리스트", description = "가 보고 싶은 장소를 담아 두고 일정에 넣는다")
public class PlaceWishlistController {

    private final PlaceWishlistService wishlistService;

    public PlaceWishlistController(PlaceWishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @PostMapping("/places/{placeId}/wishlists")
    @Operation(
            summary = "위시리스트에 담기",
            description = "이미 담은 상태에서 다시 요청해도 중복 생성되지 않는다."
    )
    public PlaceWishlistToggleResponse add(
            @AuthenticationPrincipal AuthenticatedUser loginUser,
            @Parameter(example = "42") @PathVariable Long placeId
    ) {
        Long userId = LoginUser.require(loginUser);
        return wishlistService.add(placeId, userId);
    }

    @DeleteMapping("/places/{placeId}/wishlists")
    @Operation(
            summary = "위시리스트에서 빼기",
            description = "담지 않은 상태에서 요청해도 오류가 아니다."
    )
    public PlaceWishlistToggleResponse remove(
            @AuthenticationPrincipal AuthenticatedUser loginUser,
            @Parameter(example = "42") @PathVariable Long placeId
    ) {
        Long userId = LoginUser.require(loginUser);
        return wishlistService.remove(placeId, userId);
    }

    @GetMapping("/users/me/wishlists")
    @Operation(
            summary = "내 위시리스트",
            description = "최근에 담은 순으로 반환한다. 담은 뒤 가려진 장소는 제외한다. "
                    + "여기서 받은 placeId 를 일정 생성의 mustVisitPlaceIds 에 그대로 넣는다."
    )
    public PlaceWishlistListResponse getMyWishlist(
            @AuthenticationPrincipal AuthenticatedUser loginUser,
            @Parameter(description = "0부터 시작하는 페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "한 번에 가져올 장소 수. 1 이상 50 이하", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) Integer size
    ) {
        Long userId = LoginUser.require(loginUser);
        return wishlistService.getMyWishlist(userId, page, size);
    }
}
