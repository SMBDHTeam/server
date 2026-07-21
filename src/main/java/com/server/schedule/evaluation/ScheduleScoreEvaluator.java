package com.server.schedule.evaluation;

import com.server.place.support.TourApiTheme;
import com.server.place.support.TourApiThemeMapper;
import com.server.schedule.dto.ScheduleCreateRequest;
import com.server.schedule.dto.ScheduleResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class ScheduleScoreEvaluator {

public ScheduleScoreResult evaluate(ScheduleCreateRequest request, ScheduleResponse response) {
        List<ScheduleScoreResult.Metric> metrics = List.of(
                timeFit(request, response),
                mobilityFit(request, response),
                transitFit(request, response),
                preferenceFit(request, response),
                endpointFit(response)
        );
        int evaluatedMaxScore = metrics.stream()
                .filter(metric -> "EVALUATED".equals(metric.status()))
                .mapToInt(ScheduleScoreResult.Metric::maxScore)
                .sum();
        int rawScore = metrics.stream()
                .filter(metric -> "EVALUATED".equals(metric.status()))
                .mapToInt(ScheduleScoreResult.Metric::score)
                .sum();
        int totalScore = evaluatedMaxScore == 0
                ? 0 : (int) Math.round(rawScore * 100.0 / evaluatedMaxScore);
        return new ScheduleScoreResult(totalScore, metrics);
    }

    private ScheduleScoreResult.Metric timeFit(ScheduleCreateRequest request, ScheduleResponse response) {
        int maxOverrunMinutes = response.days()
                .stream()
                .mapToInt(day -> Math.max(0, usedMinutes(day) - availableMinutes(request, day)))
                .max()
                .orElse(0);
        int maxUnusedMinutes = response.days()
                .stream()
                .mapToInt(day -> Math.max(0, availableMinutes(request, day) - usedMinutes(day)))
                .max()
                .orElse(0);
        int unusedPenalty = Math.min(10, Math.max(0, maxUnusedMinutes - 90) / 30);
        int score = Math.max(0, 30 - maxOverrunMinutes / 5 - unusedPenalty);
        String reason = maxOverrunMinutes > 0
                ? "최대 " + maxOverrunMinutes + "분 초과"
                : maxUnusedMinutes > 90
                        ? "가장 긴 미사용 시간 " + maxUnusedMinutes + "분"
                        : "일별 가용 시간 안에 들어옴";
        return new ScheduleScoreResult.Metric(
                "TIME_FIT",
                "일정 시간 적합성",
                30,
                score,
                reason
        );
    }

    private int availableMinutes(ScheduleCreateRequest request, ScheduleResponse.Day day) {
        if (day.startTime() != null && day.endTime() != null) {
            return (int) Duration.between(day.startTime(), day.endTime()).toMinutes();
        }
        return (int) Duration.between(request.dailyStartTime(), request.dailyEndTime()).toMinutes();
    }

    private int usedMinutes(ScheduleResponse.Day day) {
        int stopMinutes = day.stops()
                .stream()
                .mapToInt(stop -> stop.stayMinutes() + transitMinutes(stop.inboundTransit()))
                .sum();
        return stopMinutes + transitMinutes(day.finalTransit());
    }

    private ScheduleScoreResult.Metric mobilityFit(ScheduleCreateRequest request, ScheduleResponse response) {
        boolean lowBurden = hasAnswer(request, "COMPANION_PARENTS")
                || hasAnswer(request, "COMPANION_FAMILY_WITH_CHILD")
                || hasAnswer(request, "MOBILITY_AVOID_HILLS_STAIRS")
                || hasAnswer(request, "MOBILITY_LOW_WALK");
        int walkOnlyMinutes = walkOnlyMinutes(response);
        int burdenPlaceCount = burdenPlaceCount(response);
        int score = lowBurden
                ? Math.max(0, 25 - walkOnlyMinutes / 3 - burdenPlaceCount * 8)
                : hasAnswer(request, "PROMPT_LOW_WALKING")
                        ? Math.max(0, 25 - walkOnlyMinutes / 5)
                        : Math.max(0, 25 - walkOnlyMinutes / 8);
        return new ScheduleScoreResult.Metric(
                "MOBILITY_FIT",
                "도보·언덕 부담 적합성",
                25,
                score,
                "도보 전용 이동 " + walkOnlyMinutes + "분, 부담 장소 " + burdenPlaceCount + "개"
        );
    }

    private int walkOnlyMinutes(ScheduleResponse response) {
        return response.days()
                .stream()
                .mapToInt(day -> day.stops()
                        .stream()
                        .mapToInt(stop -> walkOnlyMinutes(stop.inboundTransit()))
                        .sum() + walkOnlyMinutes(day.finalTransit()))
                .sum();
    }

    private int walkOnlyMinutes(ScheduleResponse.Transit transit) {
        if (transit == null || transit.segments().isEmpty()) {
            return 0;
        }
        boolean walkOnly = transit.segments()
                .stream()
                .allMatch(segment -> "WALK".equals(segment.mode()));
        return walkOnly ? transit.totalMinutes() : 0;
    }

    private int burdenPlaceCount(ScheduleResponse response) {
        return (int) response.days()
                .stream()
                .flatMap(day -> day.stops().stream())
                .filter(stop -> containsAny(stop.place().name(), "감천", "흰여울", "이바구", "산복", "계단", "전망대", "중앙공원"))
                .count();
    }

    private ScheduleScoreResult.Metric transitFit(ScheduleCreateRequest request, ScheduleResponse response) {
        List<ScheduleResponse.Transit> transits = response.days()
                .stream()
                .flatMap(day -> java.util.stream.Stream.concat(
                        day.stops().stream().map(ScheduleResponse.Stop::inboundTransit),
                        java.util.stream.Stream.of(day.finalTransit())))
                .filter(Objects::nonNull)
                .toList();
        int transferBurden = transits.stream().mapToInt(this::transferBurden).sum();
        int penaltyUnit = hasAnswer(request, "TRANSIT_SIMPLE") ? 8 : 5;
        int normalizedPenalty = transits.isEmpty()
                ? 0
                : (transferBurden * penaltyUnit + transits.size() - 1) / transits.size();
        int score = Math.max(0, 20 - normalizedPenalty);
        double averageBurden = transits.isEmpty() ? 0.0 : transferBurden * 1.0 / transits.size();
        return new ScheduleScoreResult.Metric(
                "TRANSIT_FIT",
                "환승 단순성",
                20,
                score,
                "복합 대중교통 구간 부담 " + transferBurden
                        + ", 이동 구간 " + transits.size()
                        + "개, 구간당 " + String.format(java.util.Locale.ROOT, "%.2f", averageBurden)
        );
    }

    private int transferBurden(ScheduleResponse.Transit transit) {
        if (transit == null) {
            return 0;
        }
        long transitSegmentCount = transit.segments()
                .stream()
                .filter(segment -> !"WALK".equals(segment.mode()))
                .count();
        return (int) Math.max(0, transitSegmentCount - 1);
    }

    private ScheduleScoreResult.Metric preferenceFit(ScheduleCreateRequest request, ScheduleResponse response) {
        int score = 15;
        if (hasAnswer(request, "PACE_RELAXED") && maxStopsPerDay(response) > 3) {
            score -= 4;
        }
        List<String> themeAnswers = answerIds(request, "THEME");
        if (!themeAnswers.isEmpty() && !matchesAnyTheme(response, themeAnswers)) {
            score -= 5;
        }
        if (hasAnswer(request, "PROMPT_PREFER_SEA_VIEW")
                && !matchesAnyTheme(response, List.of("THEME_NATURE"))) score -= 2;
        if (hasAnswer(request, "PROMPT_PREFER_FOOD")
                && !matchesAnyTheme(response, List.of("THEME_FOOD"))) score -= 2;
        return new ScheduleScoreResult.Metric(
                "PREFERENCE_FIT",
                "취향 반영",
                15,
                Math.max(0, score),
                "선택 답변과 장소명 기반 선호 일치 평가"
        );
    }

    private int maxStopsPerDay(ScheduleResponse response) {
        return response.days()
                .stream()
                .mapToInt(day -> day.stops().size())
                .max()
                .orElse(0);
    }

    private boolean matchesAnyTheme(ScheduleResponse response, List<String> themeAnswerIds) {
        return response.days().stream()
                .flatMap(day -> day.stops().stream())
                .anyMatch(stop -> themeAnswerIds.stream()
                        .anyMatch(answerId -> themeMatches(answerId, stop.place().name())));
    }

    private boolean themeMatches(String themeAnswerId, String placeName) {
        if (TourApiTheme.fromAnswerId(themeAnswerId).isPresent()) {
            return TourApiThemeMapper.matchesThemeText(themeAnswerId, placeName);
        }
        return switch (themeAnswerId) {
            case "THEME_LOCAL" -> containsAny(placeName, "로컬", "시장", "거리", "마을", "골목");
            case "THEME_FOOD" -> containsAny(placeName, "식당", "맛집", "시장", "카페");
            case "THEME_CULTURE", "THEME_HISTORY_CULTURE" ->
                    containsAny(placeName, "역사", "문화", "박물관", "기념관", "유적");
            case "THEME_NATURE" -> containsAny(placeName, "해수욕장", "바다", "공원", "산책로", "섬", "숲");
            case "THEME_ACTIVITY" -> containsAny(placeName, "체험", "레저", "요트", "케이블카", "테마파크");
            case "THEME_SEA" -> containsAny(placeName, "해수욕장", "바다", "해변", "광안", "송정");
            case "THEME_SHOPPING" -> containsAny(placeName, "쇼핑", "백화점", "아울렛", "몰", "시장");
            case "THEME_HEALING" -> containsAny(placeName, "공원", "숲", "산책", "수목원", "카페", "온천");
            case "THEME_NIGHT_VIEW" -> containsAny(placeName, "야경", "전망", "타워", "광안", "해변");
            case "THEME_EVENT" -> containsAny(placeName, "축제", "페스타", "행사", "BIFF", "불꽃");
            default -> true;
        };
    }

    private ScheduleScoreResult.Metric endpointFit(ScheduleResponse response) {
        if (response.planningAssumptions() != null
                && "ATTRACTION_ROUTES_ONLY".equals(response.planningAssumptions().routeCoverage())) {
            return new ScheduleScoreResult.Metric(
                    "ENDPOINT_FIT", "최종 도착 경로", 10, 0,
                    "숙소 미정으로 일차 경계 경로를 평가하지 않음", "NOT_EVALUATED");
        }
        boolean hasFinalTransit = !response.days().isEmpty()
                && response.days().stream().allMatch(day -> day.finalTransit() != null
                        || "LAST_STOP".equals(day.endLocationSource()));
        return new ScheduleScoreResult.Metric(
                "ENDPOINT_FIT",
                "최종 도착 경로",
                10,
                hasFinalTransit ? 10 : 0,
                hasFinalTransit ? "모든 일차의 최종 도착 경로 포함" : "최종 도착 경로가 없는 일차 존재"
        );
    }

    private int transitMinutes(ScheduleResponse.Transit transit) {
        return transit == null ? 0 : transit.totalMinutes();
    }

    private boolean hasAnswer(ScheduleCreateRequest request, String answerId) {
        return request.selectedAnswers()
                .stream()
                .anyMatch(answer -> answerId.equals(answer.answerId()));
    }

    private List<String> answerIds(ScheduleCreateRequest request, String questionId) {
        return request.selectedAnswers()
                .stream()
                .filter(answer -> questionId.equals(answer.questionId()))
                .map(ScheduleCreateRequest.SelectedAnswer::answerId)
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean containsAny(String value, String... tokens) {
        String target = value == null ? "" : value;
        for (String token : tokens) {
            if (target.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
