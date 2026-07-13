package com.server.schedule.evaluation;

import com.server.schedule.dto.ScheduleCreateRequest;
import com.server.schedule.dto.ScheduleResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
        int totalScore = metrics.stream()
                .mapToInt(ScheduleScoreResult.Metric::score)
                .sum();
        return new ScheduleScoreResult(totalScore, metrics);
    }

    private ScheduleScoreResult.Metric timeFit(ScheduleCreateRequest request, ScheduleResponse response) {
        int maxOverrunMinutes = response.days()
                .stream()
                .mapToInt(day -> Math.max(0, usedMinutes(day) - availableMinutes(request, day)))
                .max()
                .orElse(0);
        int score = Math.max(0, 30 - maxOverrunMinutes / 5);
        return new ScheduleScoreResult.Metric(
                "TIME_FIT",
                "일정 시간 적합성",
                30,
                score,
                maxOverrunMinutes == 0 ? "일별 가용 시간 안에 들어옴" : "최대 " + maxOverrunMinutes + "분 초과"
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
        int transferBurden = response.days()
                .stream()
                .mapToInt(day -> day.stops()
                        .stream()
                        .mapToInt(stop -> transferBurden(stop.inboundTransit()))
                        .sum() + transferBurden(day.finalTransit()))
                .sum();
        int penaltyUnit = hasAnswer(request, "TRANSIT_SIMPLE") ? 4 : 7;
        int score = Math.max(0, 20 - transferBurden * penaltyUnit);
        return new ScheduleScoreResult.Metric(
                "TRANSIT_FIT",
                "환승 단순성",
                20,
                score,
                "복합 대중교통 구간 부담 " + transferBurden
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
        Optional<String> themeAnswer = answerId(request, "THEME");
        if (themeAnswer.isPresent() && response.days().stream().flatMap(day -> day.stops().stream()).noneMatch(stop -> themeMatches(themeAnswer.get(), stop.place().name()))) {
            score -= 5;
        }
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

    private boolean themeMatches(String themeAnswerId, String placeName) {
        return switch (themeAnswerId) {
            case "THEME_LOCAL" -> containsAny(placeName, "로컬", "시장", "거리", "마을", "골목");
            case "THEME_FOOD" -> containsAny(placeName, "식당", "맛집", "시장", "카페");
            case "THEME_HISTORY_CULTURE" -> containsAny(placeName, "역사", "문화", "박물관", "기념관", "유적");
            case "THEME_NATURE" -> containsAny(placeName, "해수욕장", "바다", "공원", "산책로", "섬", "숲");
            case "THEME_NIGHT_VIEW" -> containsAny(placeName, "야경", "전망", "타워", "광안", "해변");
            case "THEME_EVENT" -> containsAny(placeName, "축제", "페스타", "행사", "BIFF", "불꽃");
            default -> true;
        };
    }

    private ScheduleScoreResult.Metric endpointFit(ScheduleResponse response) {
        boolean hasFinalTransit = !response.days().isEmpty()
                && response.days().stream().allMatch(day -> day.finalTransit() != null);
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

    private Optional<String> answerId(ScheduleCreateRequest request, String questionId) {
        return request.selectedAnswers()
                .stream()
                .filter(answer -> questionId.equals(answer.questionId()))
                .map(ScheduleCreateRequest.SelectedAnswer::answerId)
                .filter(Objects::nonNull)
                .findFirst();
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
