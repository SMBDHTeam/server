package com.server.post.dto;

import com.server.post.domain.MediaType;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "커뮤니티 게시물 작성 요청. 작성자는 X-User-Id 헤더로 전달한다.")
public record PostCreateRequest(
        @Schema(description = "본문. 최대 2000자다.",
                example = "광안리 야경 보러 갔는데 날씨가 좋았어요")
        @NotBlank @Size(max = MAX_CONTENT_LENGTH) String content,

        @Schema(description = "첨부한 사진·영상. 한 건 이상 열 건 이하다. 표시 순서는 sortOrder를 따른다.")
        @NotEmpty @Size(max = MAX_MEDIA_COUNT) @Valid List<Media> mediaList,

        @Schema(description = "게시물에 태그한 장소. 내부 places에 등록된 장소만 태그할 수 있다. 최대 열 건이다.")
        @Size(max = MAX_PLACE_TAG_COUNT) @Valid List<PlaceTag> placeTags
) {

    /** 본문 컬럼이 text 라 DB가 길이를 막지 않는다. 여기서 막지 않으면 수 MB 본문이 그대로 저장된다. */
    public static final int MAX_CONTENT_LENGTH = 2000;

    public static final int MAX_MEDIA_COUNT = 10;

    public static final int MAX_PLACE_TAG_COUNT = 10;

    public static final int MAX_URL_LENGTH = 2048;

    @Schema(description = "첨부 미디어 한 건")
    public record Media(
            @Schema(description = "업로드된 파일 URL",
                    example = "https://example.com/media/gwangalli-night.jpg")
            @NotBlank @Size(max = MAX_URL_LENGTH) String url,

            @Schema(description = "미디어 종류", example = "IMAGE")
            @NotNull MediaType mediaType,

            @Schema(description = "게시물 안에서의 표시 순서. 0부터 시작한다.", example = "0")
            @PositiveOrZero int sortOrder
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
