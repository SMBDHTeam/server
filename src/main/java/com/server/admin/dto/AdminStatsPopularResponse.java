package com.server.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "인기 장소 또는 해시태그")
public record AdminStatsPopularResponse(
        @Schema(description = "PLACE 또는 HASHTAG", example = "PLACE") String type,
        List<Item> items
) {

    @Schema(description = "인기 항목 한 건")
    public record Item(
            @Schema(description = "장소 ID. 해시태그면 null", example = "42") Long id,
            @Schema(example = "해운대해수욕장") String name,
            @Schema(description = "쓰인 횟수", example = "37") long count
    ) {
    }
}
