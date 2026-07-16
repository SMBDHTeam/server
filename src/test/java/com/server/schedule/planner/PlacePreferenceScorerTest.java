package com.server.schedule.planner;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.place.domain.Place;
import com.server.schedule.dto.ScheduleCreateRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("장소 선호도 점수")
class PlacePreferenceScorerTest {

    private final PlacePreferenceScorer scorer = new PlacePreferenceScorer();

    @Test
    @DisplayName("다중 선택 테마 중 하나와 일치하면 선호 점수를 적용한다")
    void scoresAllSelectedThemes() {
        Place restaurant = place("V2-SCORE-1", "부산 로컬 맛집", "39");
        ScheduleCreateRequest request = request(List.of(
                answer("THEME", "THEME_NATURE"),
                answer("THEME", "THEME_FOOD")
        ));

        assertThat(scorer.themeScore(restaurant, request)).isLessThan(0);
    }

    @Test
    @DisplayName("해석된 바다 선호 자유 요청을 낮은 가중치로 적용한다")
    void scoresInterpretedPromptPreference() {
        Place beach = place("V2-SCORE-2", "광안리해수욕장", "12");
        ScheduleCreateRequest request = request(List.of(
                answer("THEME", "THEME_FOOD"),
                answer("PROMPT", "PROMPT_PREFER_SEA_VIEW")
        ));

        assertThat(scorer.themeScore(beach, request)).isEqualTo(-750);
    }

    private ScheduleCreateRequest request(List<ScheduleCreateRequest.SelectedAnswer> answers) {
        ScheduleCreateRequest.Location location = new ScheduleCreateRequest.Location(
                "부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151"));
        return new ScheduleCreateRequest(
                LocalDate.parse("2026-08-10"), LocalDate.parse("2026-08-10"),
                LocalTime.of(10, 0), LocalTime.of(20, 0), location, location,
                answers, List.of(), List.of());
    }

    private ScheduleCreateRequest.SelectedAnswer answer(String questionId, String answerId) {
        return new ScheduleCreateRequest.SelectedAnswer(questionId, answerId);
    }

    private Place place(String externalId, String name, String contentTypeId) {
        return new Place(
                "TOUR_API", externalId, contentTypeId, name, "관광지", "부산",
                new BigDecimal("129.1186"), new BigDecimal("35.1532"), null);
    }
}
