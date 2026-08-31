package com.server.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 보낸 항목만 바꾼다. 생략한 항목은 그대로 둔다.
 *
 * <p>배열은 통째로 교체한다. 사진 세 장 중 하나를 빼려면 남길 두 장을 보낸다. "몇 번째를
 * 지워라" 같은 형태를 따로 두지 않는 것은, 교체 방식이면 추가·삭제·순서 변경이 한 요청으로
 * 끝나기 때문이다.
 */
@Schema(description = "게시물 수정 요청. 작성자 본인만 수정할 수 있다. 보낸 항목만 바뀐다.")
public record PostUpdateRequest(
        @Schema(description = "수정할 본문. 최대 2000자다. 생략하면 본문을 바꾸지 않는다.",
                example = "광안리 야경 진짜 좋았어요")
        @Size(max = PostCreateRequest.MAX_CONTENT_LENGTH) String content,

        @Schema(description = "교체할 사진·영상 전체. 생략하면 그대로 둔다. "
                + "보낼 때는 한 건 이상 열 건 이하여야 한다.")
        @Size(min = 1, max = PostCreateRequest.MAX_MEDIA_COUNT)
        @Valid List<PostCreateRequest.Media> mediaList,

        @Schema(description = "교체할 장소 태그 전체. 생략하면 그대로 두고, 빈 배열이면 모두 없앤다.")
        @Size(max = PostCreateRequest.MAX_PLACE_TAG_COUNT)
        @Valid List<PostCreateRequest.PlaceTag> placeTags,

        @Schema(description = "교체할 카테고리 전체. 생략하면 그대로 두고, 빈 배열이면 모두 없앤다.",
                example = "[\"맛집\"]")
        @Size(max = PostCreateRequest.MAX_CATEGORY_COUNT) List<String> categories
) {

    /** 모두 비어 있으면 바꿀 것이 없는 요청이다. */
    public boolean isEmpty() {
        return content == null && mediaList == null && placeTags == null && categories == null;
    }
}
