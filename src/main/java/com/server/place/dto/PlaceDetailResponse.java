package com.server.place.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

public record PlaceDetailResponse(
        Long id,
        @Schema(description = "장소 출처", example = "NAVER_LOCAL",
                allowableValues = {"TOUR_API", "NAVER_LOCAL", "KAKAO_LOCAL"})
        String source,
        String externalContentId,
        String contentTypeId,
        String name,
        String category,
        @Schema(description = "화면 표시용 카테고리 라벨", example = "음식점")
        String categoryLabel,
        String address,
        BigDecimal longitude,
        BigDecimal latitude,
        @Schema(description = "외부 제공자의 장소 페이지 링크. 축약 상세 화면에서 더 보기 대상으로 쓴다.",
                example = "https://map.naver.com/p/entry/place/1234567")
        String placeUrl,
        String primaryImageUrl,
        String overview,
        OperatingInfo operatingInfo,
        List<Image> images,
        @Schema(description = "내 위시리스트에 담겼는지. 로그인하지 않았으면 null", example = "true")
        Boolean wishlisted
) {

    /** 요청자 기준 값을 채운다. 장소 정보는 요청자와 무관해 서비스가 null 로 만든다. */
    public PlaceDetailResponse withWishlisted(Boolean wishlisted) {
        return new PlaceDetailResponse(
                id, source, externalContentId, contentTypeId, name, category, categoryLabel,
                address, longitude, latitude, placeUrl, primaryImageUrl, overview, operatingInfo, images,
                wishlisted);
    }

    public record OperatingInfo(
            String openingHoursText,
            String closedDaysText,
            String useFeeText,
            String parkingText,
            boolean requiresManualCheck
    ) {
    }

    public record Image(
            String url,
            String thumbnailUrl,
            String copyrightType
    ) {
    }
}
