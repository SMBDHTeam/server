package com.server.popularplace.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.place.domain.Place;
import com.server.popularplace.dto.PopularPlaceResponse;
import com.server.post.domain.Post;
import com.server.post.domain.PostPlaceTag;
import com.server.user.domain.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자가 붙인 장소 태그로 "요즘 사람들이 가는 곳"을 만든다. 사용자가 만드는 데이터라
 * 한 사람이 여러 번 붙인 것, 여러 곳을 함께 붙인 글, 오래된 글을 걸러야 목록을 믿을 수 있다.
 *
 * <p>최소 인원과 기간은 설정값이며 여기서는 기본값 2명·30일을 전제한다.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("인기 장소")
class PopularPlaceTest {

    @Autowired
    private PopularPlaceService popularPlaceService;

    @Autowired
    private EntityManager entityManager;

    private Place beach;
    private Place restaurant;

    @BeforeEach
    void setUp() {
        beach = place("광안리해수욕장");
        restaurant = place("OO국밥");
        entityManager.flush();
    }

    @Test
    @DisplayName("두 사람 이상이 태그한 장소만 준다")
    void requiresEnoughAuthors() {
        taggedPost(user("가"), beach);
        flush();
        assertThat(findPopular()).isEmpty();

        taggedPost(user("나"), beach);
        flush();
        assertThat(findPopular())
                .singleElement()
                .satisfies(place -> {
                    assertThat(place.name()).isEqualTo("광안리해수욕장");
                    assertThat(place.authorCount()).isEqualTo(2);
                    assertThat(place.postCount()).isEqualTo(2);
                });
    }

    @Test
    @DisplayName("한 사람이 여러 번 태그해도 인원으로 세지 않는다")
    void countsAuthorsNotPosts() {
        User one = user("혼자");
        taggedPost(one, beach);
        taggedPost(one, beach);
        taggedPost(one, beach);
        flush();

        assertThat(findPopular()).isEmpty();
    }

    @Test
    @DisplayName("여러 곳을 태그한 글은 어디를 가리키는지 알 수 없어 세지 않는다")
    void ignoresPostsWithSeveralPlaces() {
        for (String name : List.of("가", "나", "다")) {
            Post post = post(user(name), "광안리 갔다가 국밥");
            entityManager.persist(new PostPlaceTag(post, null, beach));
            entityManager.persist(new PostPlaceTag(post, null, restaurant));
        }
        flush();

        assertThat(findPopular()).isEmpty();
    }

    @Test
    @DisplayName("기간이 지난 글은 세지 않는다")
    void ignoresOldPosts() {
        // "지금" 인기 있는 곳을 보여주는 목록이다. 작년에 반짝 유행한 곳이 계속 위에 남으면
        // 목록이 갱신되지 않는다.
        oldTaggedPost(user("가"), beach);
        oldTaggedPost(user("나"), beach);
        flush();

        assertThat(findPopular()).isEmpty();
    }

    @Test
    @DisplayName("사람 수가 많은 장소가 위에 온다")
    void ordersByAuthorCount() {
        taggedPost(user("가"), beach);
        taggedPost(user("나"), beach);
        taggedPost(user("다"), beach);
        taggedPost(user("라"), restaurant);
        taggedPost(user("마"), restaurant);
        flush();

        assertThat(findPopular())
                .extracting(PopularPlaceResponse::name)
                .containsExactly("광안리해수욕장", "OO국밥");
    }

    @Test
    @DisplayName("가려 둔 장소는 빼고 준다")
    void ignoresHiddenPlace() {
        taggedPost(user("가"), beach);
        taggedPost(user("나"), beach);
        beach.hide("테스트");
        flush();

        assertThat(findPopular()).isEmpty();
    }

    private List<PopularPlaceResponse> findPopular() {
        return popularPlaceService.findPopularPlaces(20).items();
    }

    private void taggedPost(User author, Place place) {
        Post post = post(author, "여기 좋다");
        entityManager.persist(new PostPlaceTag(post, null, place));
    }

    /** 기간 밖으로 밀어낸 글. 작성 시각은 생성자가 정하므로 뒤로 돌려놓는다. */
    private void oldTaggedPost(User author, Place place) {
        Post post = post(author, "작년에 갔던 곳");
        ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.now().minusDays(60));
        entityManager.persist(new PostPlaceTag(post, null, place));
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
