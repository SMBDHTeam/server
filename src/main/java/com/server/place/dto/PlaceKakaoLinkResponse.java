package com.server.place.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 장소를 카카오맵에서 보여줄 주소.
 *
 * @param kakaoPlaceId 같은 장소로 판단한 카카오 장소 ID. 찾지 못했으면 null
 * @param url          카카오 장소 상세 페이지. 찾지 못했으면 이름 검색 결과 페이지
 * @param matched      카카오 장소를 찾았는지. false 면 화면이 "검색 결과"라고 알려준다
 */
@Schema(description = "카카오맵 장소 페이지 주소")
public record PlaceKakaoLinkResponse(
        @Schema(example = "42") Long placeId,
        @Schema(description = "카카오 장소 ID. 찾지 못했으면 null", example = "7913306") String kakaoPlaceId,
        @Schema(description = "앱 안에서 띄울 카카오맵 페이지", example = "https://place.map.kakao.com/7913306")
        String url,
        @Schema(description = "같은 장소를 찾았는지. false 면 url 이 이름 검색 결과다", example = "true")
        boolean matched
) {

    private static final String PLACE_PAGE = "https://place.map.kakao.com/";

    public static PlaceKakaoLinkResponse matched(Long placeId, String kakaoPlaceId) {
        return new PlaceKakaoLinkResponse(placeId, kakaoPlaceId, PLACE_PAGE + kakaoPlaceId, true);
    }

    public static PlaceKakaoLinkResponse search(Long placeId, String searchUrl) {
        return new PlaceKakaoLinkResponse(placeId, null, searchUrl, false);
    }
}
