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
        @Schema(
                description = """
                        제공 가능한 상세 정보의 수준.

                        FULL: 소개글·운영정보·이미지 중 하나 이상을 제공한다. TourAPI로 적재한 장소가 해당한다.
                        BASIC: 이름·주소·카테고리·외부 링크만 제공한다. 사용자가 외부 검색으로 직접 등록한 장소가 해당한다.

                        BASIC 응답에서 overview, operatingInfo, images가 비어 있는 것은 조회 실패가 아니라 정상이다.
                        클라이언트는 이 값으로 상세 화면 구성을 나누고, BASIC에서는 placeUrl로 외부 링크를 제공한다.""",
                example = "BASIC",
                allowableValues = {"FULL", "BASIC"})
        String detailLevel,
        String overview,
        OperatingInfo operatingInfo,
        List<Image> images
) {

    public static final String DETAIL_LEVEL_FULL = "FULL";
    public static final String DETAIL_LEVEL_BASIC = "BASIC";

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
