package com.server.schedule.controller;

/**
 * Preview 요청 예시.
 *
 * <p>V2 생성 흐름의 진입점이라 중첩 구조가 많다. 예시가 없으면 클라이언트가
 * {@code lodgingPlan}, {@code selectedAnswers}, {@code dayOverrides} 형태를
 * 명세에서 일일이 찾아 조립해야 한다.
 *
 * <p>{@code placeId}는 환경마다 다르므로 예시에서는 비워 둔다. 실제 값은
 * {@code GET /api/v1/places} 또는 {@code POST /api/v1/places/resolve} 응답에서 얻는다.
 */
final class SchedulePreviewOpenApiExamples {

    /**
     * 기본 생성 화면이 실제로 보내는 요청.
     *
     * <p>프론트 1차 범위에서는 {@code startTime}, {@code endConstraint},
     * {@code customPrompt} 를 보내지 않고 {@code fixedEvents}, {@code dayOverrides} 는
     * 빈 배열로 둔다. 서버가 일차별 시간을 채우고 결과를 응답의 appliedDefaults 로 알린다.
     */
    static final String FRONTEND_BASIC = """
            {
              "startDate": "2026-09-20",
              "endDate": "2026-09-21",
              "startLocation": {
                "name": "부산역",
                "longitude": 129.0403,
                "latitude": 35.1151
              },
              "lodgingPlan": {
                "mode": "UNDECIDED"
              },
              "selectedAnswers": [
                {"questionId": "COMPANION", "answerIds": ["COMPANION_FRIENDS"]},
                {"questionId": "MOBILITY", "answerIds": ["MOBILITY_NORMAL"]},
                {"questionId": "PACE", "answerIds": ["PACE_RELAXED"]},
                {"questionId": "TRANSIT", "answerIds": ["TRANSIT_SIMPLE"]},
                {"questionId": "THEME", "answerIds": ["THEME_NATURE", "THEME_FOOD"]}
              ],
              "mustVisitPlaceIds": [],
              "fixedEvents": [],
              "dayOverrides": []
            }
            """;

    /**
     * API 가 지원하는 전체 입력. 프론트 1차 범위에서는 Deferred 다.
     *
     * <p>고정 숙소, 종료 제약, 일차별 시간 조정, 자유 요청을 함께 보낸다.
     */
    static final String FULL_OPTIONS = """
            {
              "startDate": "2026-09-20",
              "endDate": "2026-09-22",
              "startLocation": {
                "name": "부산역",
                "longitude": 129.0403,
                "latitude": 35.1151
              },
              "startTime": "10:00",
              "lodgingPlan": {
                "mode": "FIXED",
                "baseLocation": {
                  "name": "해운대 숙소",
                  "longitude": 129.1604,
                  "latitude": 35.1587
                }
              },
              "endConstraint": {
                "type": "FLIGHT_DEPARTURE",
                "location": {
                  "name": "김해국제공항",
                  "longitude": 128.9485,
                  "latitude": 35.1732
                },
                "targetAt": "2026-09-22T20:00:00+09:00"
              },
              "selectedAnswers": [
                {"questionId": "COMPANION", "answerIds": ["COMPANION_COUPLE"]},
                {"questionId": "MOBILITY", "answerIds": ["MOBILITY_LOW_WALK"]},
                {"questionId": "PACE", "answerIds": ["PACE_RELAXED"]},
                {"questionId": "TRANSIT", "answerIds": ["TRANSIT_SIMPLE"]},
                {"questionId": "THEME", "answerIds": ["THEME_NATURE"]}
              ],
              "mustVisitPlaceIds": [],
              "fixedEvents": [],
              "dayOverrides": [
                {
                  "date": "2026-09-21",
                  "startTime": "09:00",
                  "endTime": "21:00"
                }
              ],
              "customPrompt": "바다를 많이 보고 걷는 구간은 적었으면 좋겠어요",
              "timeZone": "Asia/Seoul"
            }
            """;

    private SchedulePreviewOpenApiExamples() {
    }
}
