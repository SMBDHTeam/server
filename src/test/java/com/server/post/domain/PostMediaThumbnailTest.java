package com.server.post.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("사진의 목록용 주소")
class PostMediaThumbnailTest {

    private static final String ORIGINAL = "https://bucket.s3.ap-northeast-2.amazonaws.com/posts/a.jpg";
    private static final String THUMBNAIL = "https://bucket.s3.ap-northeast-2.amazonaws.com/posts/a_thumb.jpg";

    @Test
    @DisplayName("축소본이 있으면 축소본을 쓴다")
    void prefersThumbnail() {
        PostMedia media = new PostMedia(null, MediaType.IMAGE, ORIGINAL, THUMBNAIL, 0);

        assertThat(media.getListImageUrl()).isEqualTo(THUMBNAIL);
        assertThat(media.getUrl()).isEqualTo(ORIGINAL);
    }

    @Test
    @DisplayName("축소본이 없으면 원본으로 돌아간다")
    void fallsBackToOriginal() {
        // 영상, webp, 그리고 이 열이 생기기 전에 올라온 사진이 여기에 해당한다.
        assertThat(new PostMedia(null, MediaType.IMAGE, ORIGINAL, null, 0).getListImageUrl())
                .isEqualTo(ORIGINAL);
        assertThat(new PostMedia(null, MediaType.IMAGE, ORIGINAL, "  ", 0).getListImageUrl())
                .isEqualTo(ORIGINAL);
        assertThat(new PostMedia(null, MediaType.IMAGE, ORIGINAL, 0).getListImageUrl())
                .isEqualTo(ORIGINAL);
    }
}
