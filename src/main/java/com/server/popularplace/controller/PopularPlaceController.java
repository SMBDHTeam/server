package com.server.popularplace.controller;

import com.server.popularplace.dto.PopularPlaceListResponse;
import com.server.popularplace.service.PopularPlaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경로가 {@code /places/popular} 라 {@code PlaceController} 의 {@code /places/{placeId}} 와
 * 같은 자리에 온다. Spring 은 글자가 그대로 맞는 쪽을 먼저 고르므로 이 매핑이 이긴다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/places")
@Tag(name = "인기 장소", description = "커뮤니티에서 많이 언급된 장소")
public class PopularPlaceController {

    private final PopularPlaceService popularPlaceService;

    public PopularPlaceController(PopularPlaceService popularPlaceService) {
        this.popularPlaceService = popularPlaceService;
    }

    @GetMapping("/popular")
    @Operation(
            summary = "인기 장소",
            description = "최근 커뮤니티에서 많이 언급된 장소를 언급한 사람이 많은 순으로 반환한다. "
                    + "홈 화면과 일정 만들 때 장소를 고르는 화면에 쓴다. "
                    + "여기서 받은 placeId 를 위시리스트나 일정의 mustVisitPlaceIds 에 그대로 넣는다."
    )
    public PopularPlaceListResponse getPopularPlaces(
            @Parameter(description = "가져올 장소 수. 1 이상 50 이하", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) Integer size
    ) {
        return popularPlaceService.findPopularPlaces(size);
    }
}
