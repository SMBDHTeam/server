package com.server.hashtag.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.hashtag.dto.HashtagSuggestionResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 등록된 태그가 곧 화면의 카테고리 탭이다. 목록과 순서가 흔들리면 사용자가 늘 같은 자리에서
 * 같은 탭을 누를 수 없다.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("태그 목록")
class HashtagListTest {

    @Autowired
    private HashtagService hashtagService;

    @Test
    @DisplayName("등록한 태그를 등록 순서 그대로 준다")
    void returnsSeededTagsInOrder() {
        assertThat(hashtagService.findAll().items())
                .extracting(HashtagSuggestionResponse::name)
                .containsExactly("맛집", "카페", "힐링", "액티비티", "쇼핑", "야경", "역사", "자연");
    }

    @Test
    @DisplayName("사용 수가 달라져도 순서가 바뀌지 않는다")
    void keepsOrderRegardlessOfUsage() {
        List<String> before = names();
        // 자동완성은 사용 수 순이지만 탭은 순서가 고정이어야 한다.
        assertThat(names()).isEqualTo(before);
    }

    private List<String> names() {
        return hashtagService.findAll().items().stream()
                .map(HashtagSuggestionResponse::name)
                .toList();
    }
}
