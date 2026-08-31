package com.server.hashtag.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.hashtag.dto.HashtagPlaceResponse;
import com.server.place.domain.Place;
import com.server.post.domain.Post;
import com.server.post.domain.PostPlaceTag;
import com.server.user.domain.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 해시태그는 장소가 아니라 글에 붙는다. 그대로 세면 "해운대 갔다가 저녁에 국밥집" 같은
 * 글이 두 곳 모두를 맛집으로 만들고, 한 사람이 잘못 붙인 태그도 순위에 오른다.
 * 그 두 가지를 거르는지 실제 집계 쿼리로 확인한다.
 *
 * <p>최소 인원은 설정값이며 여기서는 기본값 3을 전제한다. 데이터가 쌓여 기준을 올리면
 * 이 테스트의 인원도 함께 올려야 한다.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("해시태그가 가리키는 장소")
class HashtagPlaceTest {

    /** 등록된 태그만 붙으므로 시드에 있는 이름을 쓴다. */
    private static final String TAG = "맛집";

    @Autowired
    private HashtagService hashtagService;

    @Autowired
    private EntityManager entityManager;

    private Place restaurant;
    private Place beach;

    @BeforeEach
    void setUp() {
        restaurant = place("OO국밥");
        beach = place("해운대해수욕장");
        entityManager.flush();
    }

    @Test
    @DisplayName("세 사람 이상이 언급한 장소만 준다")
    void requiresEnoughAuthors() {
        // 두 사람만 언급한 상태다.
        taggedPost(user("가"), restaurant, TAG);
        taggedPost(user("나"), restaurant, TAG);
        flush();
        assertThat(findPlaces()).isEmpty();

        taggedPost(user("다"), restaurant, TAG);
        flush();
        assertThat(findPlaces())
                .singleElement()
                .satisfies(place -> {
                    assertThat(place.name()).isEqualTo("OO국밥");
                    assertThat(place.authorCount()).isEqualTo(3);
                });
    }

    @Test
    @DisplayName("한 사람이 여러 번 태그해도 인원으로 세지 않는다")
    void countsAuthorsNotPosts() {
        User one = user("혼자");
        taggedPost(one, restaurant, TAG);
        taggedPost(one, restaurant, TAG);
        taggedPost(one, restaurant, TAG);
        flush();

        // 글은 셋이지만 사람은 하나라 목록에 오르지 않는다.
        assertThat(findPlaces()).isEmpty();
    }

    @Test
    @DisplayName("같은 장소를 사진 여러 장에 붙여도 한 곳으로 센다")
    void countsPostOnceWhenSamePlaceOnSeveralPhotos() {
        for (String name : List.of("가", "나", "다")) {
            Post post = post(user(name), "여기 좋다");
            // 사진 두 장에 같은 장소를 붙였다. 가리키는 곳이 하나이므로 세야 한다.
            entityManager.persist(new PostPlaceTag(post, null, restaurant));
            entityManager.persist(new PostPlaceTag(post, null, restaurant));
            hashtagService.attach(post, List.of(TAG));
        }
        flush();

        assertThat(findPlaces())
                .singleElement()
                .satisfies(place -> assertThat(place.authorCount()).isEqualTo(3));
    }

    @Test
    @DisplayName("사진마다 다른 곳을 태그한 글은 어디를 가리키는지 알 수 없어 세지 않는다")
    void ignoresPostsWithSeveralPlaces() {
        // 세 사람이 태그했지만 모두 "해운대 들렀다가 국밥집" 형태다.
        for (String name : List.of("가", "나", "다")) {
            Post post = post(user(name), "해운대 갔다가 국밥 #맛집");
            entityManager.persist(new PostPlaceTag(post, null, beach));
            entityManager.persist(new PostPlaceTag(post, null, restaurant));
            hashtagService.attach(post, List.of("야경", "카페"));
        }
        flush();

        assertThat(findPlaces()).isEmpty();
    }

    @Test
    @DisplayName("다른 태그가 붙은 글은 섞이지 않는다")
    void separatesByTag() {
        for (String name : List.of("가", "나", "다")) {
            taggedPost(user(name), beach, "야경");
        }
        flush();

        assertThat(findPlaces()).isEmpty();
        assertThat(hashtagService.findPlaces("야경", 20).items())
                .singleElement()
                .satisfies(place -> assertThat(place.name()).isEqualTo("해운대해수욕장"));
    }

    private List<HashtagPlaceResponse> findPlaces() {
        return hashtagService.findPlaces(TAG, 20).items();
    }

    private void taggedPost(User author, Place place, String category) {
        Post post = post(author, "여기 진짜 좋다");
        entityManager.persist(new PostPlaceTag(post, null, place));
        hashtagService.attach(post, List.of(category));
    }

    private Post post(User author, String content) {
        Post post = new Post(author, content);
        entityManager.persist(post);
        entityManager.flush();
        return post;
    }

    private User user(String nickname) {
        User user = new User(nickname + System.nanoTime(), null);
        entityManager.persist(user);
        return user;
    }

    private Place place(String name) {
        Place place = new Place(
                "TOUR_API", name + System.nanoTime(), "12", name, "관광지", "부산 어딘가",
                new BigDecimal("129.11860000"), new BigDecimal("35.15320000"), null);
        entityManager.persist(place);
        return place;
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }
}
