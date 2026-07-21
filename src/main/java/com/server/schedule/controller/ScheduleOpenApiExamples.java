package com.server.schedule.controller;

final class ScheduleOpenApiExamples {

    static final String ONE_DAY_CREATE = """
            {
              "startDate": "2026-08-04",
              "endDate": "2026-08-04",
              "dailyStartTime": "09:00",
              "dailyEndTime": "19:00",
              "startLocation": {
                "name": "부산역",
                "longitude": 129.0403,
                "latitude": 35.1151
              },
              "endLocation": {
                "name": "부산역",
                "longitude": 129.0403,
                "latitude": 35.1151
              },
              "selectedAnswers": [
                {"questionId": "COMPANION", "answerId": "COMPANION_PARENTS"},
                {"questionId": "PACE", "answerId": "PACE_PACKED"},
                {"questionId": "THEME", "answerId": "THEME_NATURE"},
                {"questionId": "MOBILITY", "answerId": "MOBILITY_LOW_WALK"},
                {"questionId": "TRANSIT", "answerId": "TRANSIT_SIMPLE"}
              ],
              "mustVisitPlaceIds": []
            }
            """;

    static final String FOUR_DAY_CREATE = """
            {
              "startDate": "2026-08-04",
              "endDate": "2026-08-07",
              "dailyStartTime": "09:00",
              "dailyEndTime": "13:00",
              "startLocation": {
                "name": "부산역",
                "longitude": 129.0403,
                "latitude": 35.1151
              },
              "endLocation": {
                "name": "김해국제공항",
                "longitude": 128.9485,
                "latitude": 35.1732
              },
              "selectedAnswers": [
                {"questionId": "COMPANION", "answerId": "COMPANION_PARENTS"},
                {"questionId": "PACE", "answerId": "PACE_PACKED"},
                {"questionId": "THEME", "answerId": "THEME_NATURE"},
                {"questionId": "MOBILITY", "answerId": "MOBILITY_LOW_WALK"},
                {"questionId": "TRANSIT", "answerId": "TRANSIT_SIMPLE"}
              ],
              "mustVisitPlaceIds": [],
              "days": [
                {
                  "dayNo": 1,
                  "startTime": "09:00",
                  "endTime": "13:00",
                  "startLocation": {"name": "부산역", "longitude": 129.0403, "latitude": 35.1151},
                  "endLocation": {"name": "광안리 숙소", "longitude": 129.1186, "latitude": 35.1532}
                },
                {
                  "dayNo": 2,
                  "startTime": "09:00",
                  "endTime": "13:00",
                  "startLocation": {"name": "광안리 숙소", "longitude": 129.1186, "latitude": 35.1532},
                  "endLocation": {"name": "남포동 숙소", "longitude": 129.0320, "latitude": 35.1000}
                },
                {
                  "dayNo": 3,
                  "startTime": "09:00",
                  "endTime": "13:00",
                  "startLocation": {"name": "남포동 숙소", "longitude": 129.0320, "latitude": 35.1000},
                  "endLocation": {"name": "서면 숙소", "longitude": 129.0596, "latitude": 35.1579}
                },
                {
                  "dayNo": 4,
                  "startTime": "09:00",
                  "endTime": "13:00",
                  "startLocation": {"name": "서면 숙소", "longitude": 129.0596, "latitude": 35.1579},
                  "endLocation": {"name": "김해국제공항", "longitude": 128.9485, "latitude": 35.1732}
                }
              ]
            }
            """;

    static final String UPDATE = """
            {
              "stops": [
                {
                  "stopId": "00000000-0000-0000-0000-000000000001",
                  "placeId": null,
                  "dayNo": 1,
                  "order": 1,
                  "stayMinutes": 90
                },
                {
                  "stopId": null,
                  "placeId": 1,
                  "dayNo": 1,
                  "order": 2,
                  "stayMinutes": 60
                }
              ]
            }
            """;

    private ScheduleOpenApiExamples() {
    }
}
