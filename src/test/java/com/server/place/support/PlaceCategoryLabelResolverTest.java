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
    @DisplayName("외부 제공자의 자유 형식 카테고리는 그대로 쓴다")
    void keepsFreeFormExternalCategory() {
        assertThat(PlaceCategoryLabelResolver.resolve("음식점>한식>육류,고기요리", null))
                .isEqualTo("음식점>한식>육류,고기요리");
    }

    @Test
    @DisplayName("카테고리가 없으면 콘텐츠 유형으로, 그것도 없으면 관광지로 본다")
    void fallsBackToContentTypeThenDefault() {
        assertThat(PlaceCategoryLabelResolver.resolve(null, "39")).isEqualTo("음식점");
        assertThat(PlaceCategoryLabelResolver.resolve(" ", null)).isEqualTo("관광지");
    }
}
