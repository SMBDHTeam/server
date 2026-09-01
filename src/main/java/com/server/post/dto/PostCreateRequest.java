package com.server.post.dto;

import com.server.post.domain.MediaType;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "커뮤니티 게시물 작성 요청. 작성자는 액세스 토큰에서 읽는다.")
public record PostCreateRequest(
        @Schema(description = "본문. 최대 2000자다.",
                example = "광안리 야경 보러 갔는데 날씨가 좋았어요")
        @NotBlank @Size(max = MAX_CONTENT_LENGTH) String content,

        @Schema(description = "첨부한 사진·영상. 한 건 이상 열 건 이하다. 표시 순서는 sortOrder를 따른다.")
        @NotEmpty @Size(max = MAX_MEDIA_COUNT) @Valid List<Media> mediaList,

        @Schema(description = "고른 카테고리. GET /api/v1/categories 의 이름을 그대로 보낸다. "
                + "등록되지 않은 이름은 무시한다.", example = "[\"맛집\", \"야경\"]")
        @Size(max = MAX_CATEGORY_COUNT) List<String> categories
) {

    /** 본문 컬럼이 text 라 DB가 길이를 막지 않는다. 여기서 막지 않으면 수 MB 본문이 그대로 저장된다. */
    public static final int MAX_CONTENT_LENGTH = 2000;

    public static final int MAX_MEDIA_COUNT = 10;

    public static final int MAX_URL_LENGTH = 2048;

    /** 등록된 카테고리 수보다 많이 보낼 이유가 없다. 늘어나면 함께 올린다. */
    public static final int MAX_CATEGORY_COUNT = 10;

    @Schema(description = "첨부 미디어 한 건")
    public record Media(
            @Schema(description = "업로드된 파일 URL",
                    example = "https://example.com/media/gwangalli-night.jpg")
            @NotBlank @Size(max = MAX_URL_LENGTH) String url,

            @Schema(description = "미디어 종류", example = "IMAGE")
            @NotNull MediaType mediaType,

            @Schema(description = "게시물 안에서의 표시 순서. 0부터 시작한다.", example = "0")
            @PositiveOrZero int sortOrder,

            @Schema(description = "이 사진에서 다녀온 장소. 일정에 담긴 장소이거나 지도 "
                    + "검색으로 확정한 내부 장소 ID 다. 붙이지 않으려면 생략한다.",
                    example = "42")
            Long placeId
    ) {
    }
}
