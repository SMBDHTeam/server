package com.server.hashtag.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.hashtag.repository.HashtagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카테고리는 우리가 정한 여덟 개뿐이다.
 *
 * <p>해시태그를 자유 입력으로 받던 시절의 시드가 남아 있어 목록이 41개로 나가던 적이 있다.
 * 시드는 없는 것만 채울 뿐 남는 것을 지우지 않아, 이름을 줄여도 DB 에는 옛 값이 그대로
 * 있었다. 화면 탭이 그만큼 늘어나므로 개수를 고정한다.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("카테고리 목록")
class RegisteredCategoriesOnlyTest {

    private static final String[] EXPECTED =
            {"맛집", "카페", "힐링", "액티비티", "쇼핑", "야경", "역사", "자연"};

    @Autowired
    private HashtagService hashtagService;

    @Autowired
    private HashtagRepository hashtagRepository;

    @Test
    @DisplayName("정한 여덟 개만 있고 옛 해시태그는 없다")
    void hasOnlyRegisteredCategories() {
        assertThat(hashtagService.findAll().items())
                .extracting(item -> item.name())
                .containsExactlyInAnyOrder(EXPECTED);
    }

    @Test
    @DisplayName("옛 시드 이름은 저장소에도 남아 있지 않다")
    void oldSeedIsGone() {
        // 지우지 않으면 자동완성에서 "부산" 을 치는 순간 옛 이름이 쏟아진다.
        assertThat(hashtagRepository.findByNameIn(
                java.util.List.of("부산", "해운대", "부산맛집", "당일치기"))).isEmpty();
    }
}
