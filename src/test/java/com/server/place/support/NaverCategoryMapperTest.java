package com.server.place.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("네이버 카테고리 → TourAPI 콘텐츠 유형")
class NaverCategoryMapperTest {

    @Test
    @DisplayName("음식과 카페는 음식점 유형으로 본다")
    void mapsFoodCategories() {
        assertThat(NaverCategoryMapper.contentTypeId("음식점>한식>육류,고기요리")).isEqualTo("39");
        assertThat(NaverCategoryMapper.contentTypeId("카페,디저트>카페")).isEqualTo("39");
    }

    @Test
    @DisplayName("문화·축제·쇼핑·숙박·레포츠를 각 유형으로 나눈다")
    void mapsTheRemainingGroups() {
        assertThat(NaverCategoryMapper.contentTypeId("공연,전시>축제")).isEqualTo("15");
        assertThat(NaverCategoryMapper.contentTypeId("문화,예술>박물관")).isEqualTo("14");
        assertThat(NaverCategoryMapper.contentTypeId("쇼핑,유통>전통시장")).isEqualTo("38");
        assertThat(NaverCategoryMapper.contentTypeId("숙박>호텔")).isEqualTo("32");
        assertThat(NaverCategoryMapper.contentTypeId("스포츠,레저>수상레저")).isEqualTo("28");
    }

    @Test
    @DisplayName("분류가 없거나 모르는 값은 관광지로 둔다")
    void fallsBackToAttraction() {
        assertThat(NaverCategoryMapper.contentTypeId("여행,명소>관광,명소>해수욕장")).isEqualTo("12");
        assertThat(NaverCategoryMapper.contentTypeId("")).isEqualTo("12");
        assertThat(NaverCategoryMapper.contentTypeId(null)).isEqualTo("12");
    }
}
