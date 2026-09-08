package com.server.post.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.server.post.domain.MediaType;
import com.server.post.domain.Post;
import com.server.post.domain.PostMedia;
import com.server.user.domain.User;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 장소 태그는 사진마다 한 줄씩 저장된다.
 *
 * <p>지금 화면은 게시물당 장소를 하나만 고르므로 사진 전부에 같은 장소가 붙는다. 저장된
 * 줄을 그대로 내보내면 모아 보여주는 {@code placeTags} 에 같은 장소가 사진 수만큼 담겨,
 * 사진이 다섯 장이면 글 아래 장소 칩이 다섯 개 뜬다.
 */
@DisplayName("게시물 상세 장소 태그")
class PostDetailResponsePlaceTagsTest {

    @Test
    @DisplayName("사진 여러 장이 같은 장소면 placeTags 에는 한 번만 담는다")
    void deduplicatesPlaceTags() {
        PostDetailResponse response = PostDetailResponse.from(
                post(),
                List.of(media(20L, 0), media(21L, 1)),
                List.of(
                        new PostPlaceTagView(7L, 20L, 340L, "해운대해수욕장"),
                        new PostPlaceTagView(7L, 21L, 340L, "해운대해수욕장")),
                List.of(),
                false,
                false);

        assertThat(response.placeTags())
                .extracting(PostDetailResponse.PlaceTag::placeId)
                .containsExactly(340L);
    }

    @Test
    @DisplayName("사진마다 붙은 장소 이름은 줄이지 않는다")
    void keepsPlaceNameOnEveryMedia() {
        // placeTags 를 줄이면서 사진 쪽까지 줄이면 두 번째 사진 아래 장소가 사라진다.
        PostDetailResponse response = PostDetailResponse.from(
                post(),
                List.of(media(20L, 0), media(21L, 1)),
                List.of(
                        new PostPlaceTagView(7L, 20L, 340L, "해운대해수욕장"),
                        new PostPlaceTagView(7L, 21L, 340L, "해운대해수욕장")),
                List.of(),
                false,
                false);

        assertThat(response.mediaList())
                .extracting(PostDetailResponse.Media::sortOrder, PostDetailResponse.Media::placeName)
                .containsExactly(
                        tuple(0, "해운대해수욕장"),
                        tuple(1, "해운대해수욕장"));
    }

    @Test
    @DisplayName("서로 다른 장소는 그대로 둔다")
    void keepsDistinctPlaces() {
        PostDetailResponse response = PostDetailResponse.from(
                post(),
                List.of(media(20L, 0), media(21L, 1)),
                List.of(
                        new PostPlaceTagView(7L, 20L, 340L, "해운대해수욕장"),
                        new PostPlaceTagView(7L, 21L, 512L, "광안리해수욕장")),
                List.of(),
                false,
                false);

        assertThat(response.placeTags())
                .extracting(PostDetailResponse.PlaceTag::placeId)
                .containsExactly(340L, 512L);
    }

    private Post post() {
        User author = new User("작성자", null);
        ReflectionTestUtils.setField(author, "id", 1L);
        Post post = new Post(author, "본문");
        ReflectionTestUtils.setField(post, "id", 7L);
        return post;
    }

    private PostMedia media(Long id, int sortOrder) {
        PostMedia media =
                new PostMedia(null, MediaType.IMAGE, "https://example.com/" + id + ".jpg", sortOrder);
        ReflectionTestUtils.setField(media, "id", id);
        return media;
    }
}
