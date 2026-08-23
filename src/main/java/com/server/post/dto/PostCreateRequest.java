package com.server.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "커뮤니티 게시물 작성 요청. 작성자는 X-User-Id 헤더로 전달한다.")
public record PostCreateRequest(
        @Schema(description = "본문", example = "광안리 야경 보러 갔는데 날씨가 좋았어요")
        @NotBlank String content,

        @Schema(description = "첨부한 사진·영상. 최소 한 건이 필요하다. 표시 순서는 sortOrder를 따른다.")
        @NotEmpty @Valid List<Media> mediaList,

        @Schema(description = "게시물에 태그한 장소. 내부 places에 등록된 장소만 태그할 수 있다.")
        @Valid List<PlaceTag> placeTags
) {

    @Schema(description = "첨부 미디어 한 건")
    public record Media(
            @Schema(description = "업로드된 파일 URL",
                    example = "https://example.com/media/gwangalli-night.jpg")
            @NotBlank String url,

            @Schema(description = "미디어 종류", example = "IMAGE")
            @NotBlank String mediaType,

            @Schema(description = "게시물 안에서의 표시 순서. 0부터 시작한다.", example = "0")
            int sortOrder
    ) {
    }

    @Schema(description = "장소 태그 한 건")
    public record PlaceTag(
            @Schema(description = "내부 장소 ID", example = "42")
            @NotNull Long placeId,

            @Schema(description = "사진이 실제로 촬영된 위도. EXIF GPS가 없으면 생략한다.",
                    example = "35.15320000")
            BigDecimal latitude,

            @Schema(description = "사진이 실제로 촬영된 경도. EXIF GPS가 없으면 생략한다.",
                    example = "129.11860000")
            BigDecimal longitude
    ) {
    }
}
