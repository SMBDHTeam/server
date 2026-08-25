package com.server.admin.dto;

import com.server.place.domain.Place;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 화면의 장소 한 건")
public record AdminPlaceResponse(
        @Schema(example = "42") Long id,
        @Schema(example = "해운대해수욕장") String name,
        @Schema(example = "부산광역시 해운대구 우동") String address,
        @Schema(example = "TOUR_API") String source,
        @Schema(description = "가려져 있는지", example = "true") boolean hidden,
        @Schema(description = "가린 시각") LocalDateTime hiddenAt,
        @Schema(example = "좌표가 실제 위치와 다름") String hiddenReason
) {

    public static AdminPlaceResponse from(Place place) {
        return new AdminPlaceResponse(
                place.getId(),
                place.getName(),
                place.getAddress(),
                place.getSource(),
                place.isHidden(),
                place.getHiddenAt(),
                place.getHiddenReason());
    }
}
