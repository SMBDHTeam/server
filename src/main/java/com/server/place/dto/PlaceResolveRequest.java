package com.server.place.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Schema(description = "외부 검색으로 고른 장소를 내부 ID로 확정한다. 같은 (source, externalId)는 upsert 된다.")
public record PlaceResolveRequest(
        @Schema(description = "외부 제공자", example = "NAVER_LOCAL",
                allowableValues = {"NAVER_LOCAL", "KAKAO_LOCAL"})
        @NotBlank String source,
        @Schema(description = "제공자의 장소 ID. 네이버는 안정적인 ID가 없어 mapx-mapy 합성 키를 쓴다.",
                example = "1291598546-351585232")
        @NotBlank String externalId,
        @Schema(description = "장소명", example = "해운대 해수욕장")
        @NotBlank String name,
        @Schema(description = "제공자의 카테고리 문자열. TourAPI 콘텐츠 유형 추정에 쓴다.",
                example = "여행,명소>관광,명소>해수욕장")
        String category,
        @Schema(description = "주소", example = "부산 해운대구 우동")
        String address,
        @Schema(description = "경도. WGS84. 네이버 mapx는 10000000으로 나눈 값을 보낸다.",
                example = "129.1598546")
        @NotNull BigDecimal longitude,
        @Schema(description = "위도. WGS84", example = "35.1585232")
        @NotNull BigDecimal latitude,
        @Schema(description = "제공자의 장소 페이지 링크. 장소 상세에서 외부 지도로 연결할 때 쓴다.",
                example = "https://map.naver.com/p/entry/place/1234567")
        String placeUrl
) {
}
