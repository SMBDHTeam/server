package com.server.place.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("장소 카테고리 라벨")
class PlaceCategoryLabelResolverTest {

    @Test
    @DisplayName("TourAPI A 분류코드는 사람이 읽는 라벨로 바꾼다")
    void mapsTourApiAttractionCode() {
        assertThat(PlaceCategoryLabelResolver.resolve("A01011200", "12")).isEqualTo("자연 관광지");
    }

    @Test
    @DisplayName("A로 시작하지 않는 TourAPI 코드도 콘텐츠 유형 라벨로 바꾼다")
    void mapsTourApiLodgingCodeByContentType() {
        assertThat(PlaceCategoryLabelResolver.resolve("B02011100", "32")).isEqualTo("숙박");
        assertThat(PlaceCategoryLabelResolver.resolve("C01120001", "12")).isEqualTo("관광지");
    }

    @Test
    @DisplayName("외부 제공자의 경로형 분류는 가장 구체적인 단계의 첫 이름만 쓴다")
    void usesLeafOfExternalCategoryPath() {
        // 경로 전체를 내보내면 화면 칩에 "여행 > 관광,명소 > 해수욕장,해변" 이 그대로 찍힌다.
        assertThat(PlaceCategoryLabelResolver.resolve("여행 > 관광,명소 > 해수욕장,해변", null)).isEqualTo("해수욕장");
        assertThat(PlaceCategoryLabelResolver.resolve("여행,명소>해수욕장,해변", "12")).isEqualTo("해수욕장");
        assertThat(PlaceCategoryLabelResolver.resolve("음식점>카페", "39")).isEqualTo("카페");
        assertThat(PlaceCategoryLabelResolver.resolve("음식점>한식>육류,고기요리", null)).isEqualTo("육류");
    }

    @Test
    @DisplayName("경로가 아닌 외부 분류는 그대로 쓴다")
    void keepsPlainExternalCategory() {
        assertThat(PlaceCategoryLabelResolver.resolve("관광지", "12")).isEqualTo("관광지");
        assertThat(PlaceCategoryLabelResolver.resolve(" 카페 ", null)).isEqualTo("카페");
    }

    @Test
    @DisplayName("카테고리가 없으면 콘텐츠 유형으로, 그것도 없으면 관광지로 본다")
    void fallsBackToContentTypeThenDefault() {
        assertThat(PlaceCategoryLabelResolver.resolve(null, "39")).isEqualTo("음식점");
        assertThat(PlaceCategoryLabelResolver.resolve(" ", null)).isEqualTo("관광지");
    }
}
