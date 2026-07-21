package com.server.schedule.planner;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.external.aitheme.PlaceThemePredictionClient;
import com.server.external.aitheme.PlaceThemePredictionClient.PlaceThemeInsight;
import com.server.place.domain.Place;
import com.server.place.support.TourApiTheme;
import com.server.schedule.dto.ScheduleCreateRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
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

    @Test
    @DisplayName("모델 기반 액티비티 테마도 선호 점수를 적용한다")
    void scoresActivityTheme() {
        Place yacht = place("V2-SCORE-3", "더베이101 요트투어", "28");
        ScheduleCreateRequest request = request(List.of(
                answer("THEME", "THEME_ACTIVITY")
        ));

        assertThat(scorer.themeScore(yacht, request)).isLessThan(0);
    }

    @Test
    @DisplayName("상위 후보 중 규칙으로 못 잡는 장소는 AI 테마 예측으로 재정렬한다")
    void reranksCandidatesWithAiPrediction() {
        Place shoppingMall = customPlace("V2-SCORE-4", "중앙 쇼핑몰", "38", "도심", "기타");
        Place beach = customPlace("V2-SCORE-5", "광안리 명소", null, "해안", "기타");
        PlacePreferenceScorer aiScorer = new PlacePreferenceScorer(new StubThemePredictionClient());
        ScheduleCreateRequest request = request(List.of(answer("THEME", "THEME_NATURE")));

        List<PlacePreferenceScorer.ScoredPlace> reranked = aiScorer.rerankByAiTheme(List.of(
                new PlacePreferenceScorer.ScoredPlace(shoppingMall,
                        PlacePreferenceScorer.Neighborhood.BUSAN_STATION, 1000, 1000, 10),
                new PlacePreferenceScorer.ScoredPlace(beach,
                        PlacePreferenceScorer.Neighborhood.GWANGALLI, 1000, 1000, 50)
        ), request);

        assertThat(reranked.get(0).place().getName()).isEqualTo("광안리 명소");
    }

    @Test
    @DisplayName("AI secondary theme가 있으면 규칙으로 못 잡는 시장도 음식 테마 점수를 반영한다")
    void scoresFoodThemeFromAiSecondaryTheme() {
        Place market = customPlace("V2-SCORE-6", "자갈치시장", "38", "부산 중구", "시장");
        PlacePreferenceScorer aiScorer = new PlacePreferenceScorer(new StubThemePredictionClient());
        ScheduleCreateRequest request = request(List.of(answer("THEME", "THEME_FOOD")));

        assertThat(aiScorer.themeScore(market, request)).isLessThan(0);
    }

    @Test
    @DisplayName("저이동성 프로필에서는 AI가 비우호적으로 본 장소에 추가 페널티를 준다")
    void appliesMobilityPenaltyFromAiInsight() {
        Place hillVillage = customPlace("V2-SCORE-7", "로컬 언덕마을", "12", "부산 영도구", "마을");
        PlacePreferenceScorer aiScorer = new PlacePreferenceScorer(new StubThemePredictionClient());
        ScheduleCreateRequest request = request(List.of(answer("MOBILITY", "MOBILITY_LOW_WALK")));

        assertThat(aiScorer.mobilityPenalty(hillVillage, request)).isGreaterThan(0);
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
        return customPlace(externalId, name, contentTypeId, "부산", "관광지");
    }

    private Place customPlace(
            String externalId,
            String name,
            String contentTypeId,
            String address,
            String category
    ) {
        return new Place(
                "TOUR_API", externalId, contentTypeId, name, category, address,
                new BigDecimal("129.1186"), new BigDecimal("35.1532"), null);
    }

    private static final class StubThemePredictionClient implements PlaceThemePredictionClient {
        @Override
        public Optional<TourApiTheme> predictPrimaryTheme(Place place) {
            if (place.getName().contains("광안리")) {
                return Optional.of(TourApiTheme.NATURE);
            }
            if (place.getName().contains("자갈치")) {
                return Optional.of(TourApiTheme.SHOPPING);
            }
            return Optional.of(TourApiTheme.SHOPPING);
        }

        @Override
        public Optional<PlaceThemeInsight> predictInsight(Place place) {
            if (place.getName().contains("광안리")) {
                return Optional.of(new PlaceThemeInsight(
                        TourApiTheme.NATURE,
                        List.of(TourApiTheme.HEALING),
                        List.of("beach", "scenic_view"),
                        false,
                        true,
                        "nature_beach",
                        "자연 중심 장소"
                ));
            }
            if (place.getName().contains("자갈치")) {
                return Optional.of(new PlaceThemeInsight(
                        TourApiTheme.SHOPPING,
                        List.of(TourApiTheme.FOOD),
                        List.of("market", "food_street"),
                        true,
                        true,
                        "shopping_market",
                        "시장 + 음식 거리"
                ));
            }
            if (place.getName().contains("언덕")) {
                return Optional.of(new PlaceThemeInsight(
                        TourApiTheme.CULTURE,
                        List.of(),
                        List.of("historic_site"),
                        false,
                        false,
                        "culture_historic",
                        "경사 구간 포함"
                ));
            }
            return Optional.of(new PlaceThemeInsight(
                    TourApiTheme.SHOPPING,
                    List.of(),
                    List.of("market"),
                    false,
                    true,
                    "shopping_market",
                    "쇼핑 중심 장소"
            ));
        }
    }
}
