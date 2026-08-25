package com.server.hashtag.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("해시태그 추출")
class HashtagExtractorTest {

    private final HashtagExtractor extractor = new HashtagExtractor();

    @Test
    @DisplayName("본문에서 # 으로 시작하는 태그를 뽑는다")
    void extractsHashtags() {
        assertThat(extractor.extract("광안리 야경 #광안리맛집 #부산 최고"))
                .containsExactly("광안리맛집", "부산");
    }

    @Test
    @DisplayName("띄어쓰기에서 태그가 끝난다")
    void stopsAtWhitespace() {
        // "#광안리 맛집" 은 "광안리" 하나다. 띄어쓰기를 허용하면 어디까지가 태그인지 정할 수 없다.
        assertThat(extractor.extract("#광안리 맛집 갔다왔는데 #해운대 좋아"))
                .containsExactly("광안리", "해운대");
    }

    @Test
    @DisplayName("쉼표 같은 문자에서도 태그가 끝난다")
    void stopsAtPunctuation() {
        assertThat(extractor.extract("#광안리맛집, #부산! 그리고 #해운대."))
                .containsExactly("광안리맛집", "부산", "해운대");
    }

    @Test
    @DisplayName("영문은 소문자로 정규화해 같은 태그로 본다")
    void normalizesToLowerCase() {
        assertThat(extractor.extract("#Busan #BUSAN #busan"))
                .containsExactly("busan");
    }

    @Test
    @DisplayName("같은 태그가 여러 번 나와도 한 번만 담는다")
    void removesDuplicates() {
        assertThat(extractor.extract("#부산 여행 #부산 최고 #부산"))
                .containsExactly("부산");
    }

    @Test
    @DisplayName("등장 순서를 유지한다")
    void keepsAppearanceOrder() {
        assertThat(extractor.extract("#다 #가 #나"))
                .containsExactly("다", "가", "나");
    }

    @Test
    @DisplayName("숫자와 밑줄을 태그에 포함한다")
    void allowsDigitsAndUnderscore() {
        assertThat(extractor.extract("#부산_여행 #2026여행"))
                .containsExactly("부산_여행", "2026여행");
    }

    @Test
    @DisplayName("게시물당 20개까지만 담는다")
    void limitsCountPerPost() {
        String content = IntStream.rangeClosed(1, 25)
                .mapToObj(number -> "#태그" + number)
                .reduce("", (left, right) -> left + " " + right);

        assertThat(extractor.extract(content)).hasSize(20).endsWith("태그20");
    }

    @Test
    @DisplayName("50자를 넘는 태그는 버린다")
    void skipsTooLongHashtag() {
        String tooLong = "가".repeat(51);

        assertThat(extractor.extract("#" + tooLong + " #부산"))
                .containsExactly("부산");
    }

    @Test
    @DisplayName("# 뒤에 쓸 수 있는 문자가 없으면 태그가 아니다")
    void ignoresBareHash() {
        assertThat(extractor.extract("# 부산 #, ###")).isEmpty();
    }

    @Test
    @DisplayName("본문이 없거나 비어 있으면 빈 목록이다")
    void handlesEmptyContent() {
        assertThat(extractor.extract(null)).isEmpty();
        assertThat(extractor.extract("   ")).isEmpty();
        assertThat(extractor.extract("태그 없는 본문")).isEmpty();
    }
}
