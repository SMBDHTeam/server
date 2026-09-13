package com.server.popularplace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "인기 장소 목록. 언급한 사람이 많은 순이다.")
public record PopularPlaceListResponse(List<PopularPlaceResponse> items) {
}
