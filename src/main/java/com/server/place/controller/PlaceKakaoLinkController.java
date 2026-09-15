package com.server.place.controller;

import com.server.place.dto.PlaceKakaoLinkResponse;
import com.server.place.service.PlaceKakaoLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/places")
@Tag(name = "장소", description = "내부 DB 장소 검색과 상세 조회")
public class PlaceKakaoLinkController {

    private final PlaceKakaoLinkService placeKakaoLinkService;

    public PlaceKakaoLinkController(PlaceKakaoLinkService placeKakaoLinkService) {
        this.placeKakaoLinkService = placeKakaoLinkService;
    }

    @GetMapping("/{placeId}/kakao-link")
    @Operation(
            summary = "카카오맵 장소 페이지 주소",
            description = "장소 상세 화면이 앱 안에 띄울 카카오맵 페이지를 준다. 카카오 장소를 찾으면 장소 상세 페이지, "
                    + "못 찾거나 카카오 호출이 실패하면 이름 검색 결과 페이지다. 로그인 없이 볼 수 있다."
    )
    public PlaceKakaoLinkResponse getKakaoLink(
            @Parameter(description = "장소 ID", example = "42") @PathVariable Long placeId
    ) {
        return placeKakaoLinkService.getLink(placeId);
    }
}
