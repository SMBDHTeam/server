# 상세 일정 전달 명세 전환 초안

## 목적

현재 일정 생성 응답은 "어떤 장소를 어떤 순서로 갈지"와 "총 이동시간" 중심이다. 사용자에게 실제 상세 일정을 전달하려면 다음 정보를 함께 내려야 한다.

- 언제 출발하고 언제 도착하는지
- 어디까지 걸어가고 어느 정류장/역에서 타는지
- 환승이 몇 번이고 도보/대기 부담이 어느 정도인지
- 장소 운영정보와 일정상 주의사항이 있는지
- 추천된 이유가 사용자 답변과 어떻게 연결되는지

이 문서는 실제 `docs/API_SPEC.md` 계약을 즉시 변경하지 않는 전환 초안이다. 구현이 끝나는 시점에 `docs/API_SPEC.md`, `docs/API_FIELD_GUIDE.md`, `docs/ERD.md`, `docs/api-change-log.md`로 반영한다.

## 구현 가능성 체크

| 항목 | 구현 가능성 | 근거 | 필요 작업 |
| --- | --- | --- | --- |
| 방문지별 도착/출발 시각 | 가능 | `dailyStartTime`, 이동시간, 체류시간으로 순차 계산 가능 | `ScheduleStop`에 `arrive_at`, `depart_at` 저장 또는 응답 시 계산 |
| 경로별 출발/도착 시각 | 가능 | 방문지 시각과 `totalMinutes`로 계산 가능 | `TransitRoute` 응답 변환 로직 확장 |
| 환승 횟수 | 가능 | `BUS`, `SUBWAY` 구간 수에서 계산 가능 | `TransitRoute`에 `transfer_count` 추가 또는 응답 시 계산 |
| 도보 합계 시간 | 가능 | 현재 `WALK` 구간은 식별 가능. 구간별 시간이 없으면 총 이동시간에서 정확 분배는 제한 | ODsay `subPath.sectionTime` 파싱 필요 |
| 구간별 소요시간 | 가능 | ODsay `subPath` 단위 시간 필드를 파싱하면 가능 | `TransitRouteResult.Segment.durationMinutes` 추가 |
| 구간별 거리 | 부분 가능 | ODsay/TMAP 응답에 거리 필드가 있을 때 가능. 없으면 좌표 기반 추정 | `distanceMeters` 파싱, fallback 거리 계산 |
| 도보 안내 문장 | 가능 | 출발/도착 이름과 모드로 생성 가능 | `instruction` 생성 규칙 추가 |
| 승하차 정류장/역 ID | 부분 가능 | 현재 ODsay 응답에서 여러 후보 키를 찾고 있으나 저장하지 않음 | `start_station_id`, `end_station_id` 저장 |
| 장소 주소/카테고리/대표 이미지 | 가능 | `places`에 이미 존재 | `ScheduleResponse.Place` 확장 |
| 운영시간/휴무 원문 | 가능 | `place_operating_infos`에 존재 | 일정 조회 시 fetch/join 또는 별도 조회 |
| 추천 이유 | 가능 | 현재 후보 점수 계산 로직에서 이유를 만들 수 있음 | `selection_reasons_json` 생성/저장 |
| 사용자 주의사항 | 가능 | 도보-only, 긴 이동, 운영정보 수동확인, 최종 이동 누락 등 룰 기반 생성 가능 | `warnings_json` 생성/저장 |
| 실시간 배차/대기시간 | 부분 가능 | BIMS 연동 구조는 있으나 실제 API 안정성/정류장 매칭 검증 필요 | BIMS endpoint, station id, route id 매칭 안정화 |
| 막차 위험 | 보류 | 막차 데이터 소스와 노선별 운행 종료시각 매칭 필요 | 부산 BIS/도시철도 막차 데이터 계약 필요 |
| 혼잡 예측 | 보류 | 관광지 집중률 예측 API 적재가 필요 | TourAPI 예측 데이터 수집 파이프라인 필요 |

## 전환 후 `POST /api/v1/schedules` 응답 예시

```json
{
  "id": "schedule-uuid",
  "status": "CONFIRMED",
  "startDate": "2026-07-07",
  "endDate": "2026-07-07",
  "dailyStartTime": "09:00",
  "dailyEndTime": "18:00",
  "styleSummary": "부모님과 함께하는 로컬 중심 여유 일정",
  "days": [
    {
      "dayNo": 1,
      "date": "2026-07-07",
      "startTime": "09:00",
      "endTime": "18:00",
      "summary": "부산역에서 출발해 중앙동·영주동 로컬 장소를 둘러보고 김해공항으로 이동",
      "stops": [
        {
          "id": "stop-uuid",
          "order": 1,
          "arriveAt": "09:17",
          "departAt": "10:17",
          "stayMinutes": 60,
          "place": {
            "id": 31,
            "name": "금수사(부산)",
            "category": "관광지",
            "address": "부산광역시 ...",
            "longitude": 129.02967865,
            "latitude": 35.12042071,
            "primaryImageUrl": "https://...",
            "operatingInfo": {
              "openingHoursText": "09:00~18:00",
              "closedDaysText": "연중무휴",
              "requiresManualCheck": true
            }
          },
          "inboundTransit": {
            "routeType": "INBOUND",
            "routeOrder": 1,
            "originName": "부산역",
            "destinationName": "금수사(부산)",
            "summary": "도보 이동",
            "departAt": "09:00",
            "arriveAt": "09:17",
            "totalMinutes": 17,
            "walkMinutes": 17,
            "waitMinutes": 0,
            "transferCount": 0,
            "fareAmount": null,
            "provider": "INTERNAL_WALK",
            "realtimeStatus": "UNAVAILABLE",
            "fallbackUsed": false,
            "segments": [
              {
                "order": 1,
                "mode": "WALK",
                "lineName": null,
                "startStationId": null,
                "startStationName": "부산역",
                "endStationId": null,
                "endStationName": "금수사(부산)",
                "instruction": "부산역에서 금수사(부산)까지 도보 이동",
                "durationMinutes": 17,
                "distanceMeters": 950,
                "stationCount": null,
                "waitMinutes": 0,
                "realtimeStatus": "UNAVAILABLE"
              }
            ],
            "warnings": [
              "도보 경로는 실시간 보행 장애 정보를 반영하지 않습니다."
            ]
          },
          "selectionReasons": [
            "로컬 테마와 일치하는 장소입니다.",
            "부모님 동행 조건을 고려해 장거리 환승을 줄였습니다."
          ],
          "warnings": [
            "운영시간 원문 확인이 필요한 장소입니다."
          ]
        }
      ],
      "finalTransit": {
        "routeType": "FINAL",
        "routeOrder": 4,
        "originName": "부산 중앙공원",
        "destinationName": "김해국제공항",
        "summary": "버스 8번 + 부산-김해경전철",
        "departAt": "15:42",
        "arriveAt": "16:28",
        "totalMinutes": 46,
        "walkMinutes": 8,
        "waitMinutes": 0,
        "transferCount": 1,
        "fareAmount": 2050,
        "provider": "ODSAY",
        "realtimeStatus": "UNAVAILABLE",
        "fallbackUsed": false,
        "segments": [],
        "warnings": []
      }
    }
  ]
}
```

## 전환 후 응답 필드 설명

### 일정 루트

| 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `id` | string(UUID) | O | 일정 ID |
| `status` | string | O | 일정 상태 |
| `startDate` | string(date) | O | 여행 시작일 |
| `endDate` | string(date) | O | 여행 종료일 |
| `dailyStartTime` | string(time) | O | 기본 일일 시작시각 |
| `dailyEndTime` | string(time) | O | 기본 일일 종료시각 |
| `styleSummary` | string | X | 질문 답변 기반 여행 스타일 요약 |
| `days` | array | O | 날짜별 상세 일정 |

### `days[]`

| 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `dayNo` | integer | O | 여행 일차 |
| `date` | string(date) | O | 실제 날짜 |
| `startTime` | string(time) | O | 해당 일차 시작시각 |
| `endTime` | string(time) | O | 해당 일차 종료 목표시각 |
| `summary` | string | X | 하루 일정 요약 |
| `stops` | array | O | 방문 장소 목록 |
| `finalTransit` | object/null | X | 마지막 방문 장소에서 최종 도착지까지 이동 |

### `stops[]`

| 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `id` | string(UUID) | O | 방문 계획 ID |
| `order` | integer | O | 날짜 안 방문 순서 |
| `arriveAt` | string(time) | O | 해당 장소 도착 예정시각 |
| `departAt` | string(time) | O | 해당 장소 출발 예정시각 |
| `stayMinutes` | integer | O | 체류시간(분) |
| `place` | object | O | 방문 장소 정보 |
| `inboundTransit` | object/null | X | 이전 지점에서 이 장소까지 이동 |
| `selectionReasons` | array | O | 이 장소가 선택된 이유 |
| `warnings` | array | O | 해당 방문지 관련 주의사항 |

### `place`

| 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `id` | integer | O | 내부 장소 ID |
| `name` | string | O | 장소명 |
| `category` | string | X | 표시용 분류 |
| `address` | string | X | 주소 |
| `longitude` | number | O | 경도 |
| `latitude` | number | O | 위도 |
| `primaryImageUrl` | string/null | X | 대표 이미지 |
| `operatingInfo` | object/null | X | 운영정보 |
| `operatingInfo.openingHoursText` | string/null | X | 운영시간 원문 |
| `operatingInfo.closedDaysText` | string/null | X | 휴무일 원문 |
| `operatingInfo.requiresManualCheck` | boolean | O | 원문 직접 확인 필요 여부 |

### `inboundTransit`, `finalTransit`

| 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `routeType` | string | O | `INBOUND` 또는 `FINAL` |
| `routeOrder` | integer | O | 날짜 안 경로 순서 |
| `originName` | string | O | 경로 출발 지점명 |
| `destinationName` | string | O | 경로 도착 지점명 |
| `summary` | string | O | 사용자용 이동 요약 |
| `departAt` | string(time) | O | 이동 시작 예정시각 |
| `arriveAt` | string(time) | O | 이동 종료 예정시각 |
| `totalMinutes` | integer | O | 전체 이동시간(분) |
| `walkMinutes` | integer | O | 도보 이동 합계(분) |
| `waitMinutes` | integer | O | 대기시간 합계(분). 실시간 미연동 시 `0` 또는 추정값 |
| `transferCount` | integer | O | 환승 횟수 |
| `fareAmount` | integer/null | X | 예상 요금(원) |
| `provider` | string | O | `ODSAY`, `INTERNAL_WALK`, `FAKE`, `FALLBACK` 등 |
| `realtimeStatus` | string | O | `AVAILABLE`, `PARTIAL`, `UNAVAILABLE` |
| `fallbackUsed` | boolean | O | 외부 경로 실패 후 fallback 사용 여부 |
| `segments` | array | O | 세부 이동 구간 |
| `warnings` | array | O | 경로 관련 주의사항 |

### `segments[]`

| 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `order` | integer | O | 경로 안 구간 순서 |
| `mode` | string | O | `WALK`, `BUS`, `SUBWAY`, `FERRY`, `UNKNOWN` |
| `lineName` | string/null | X | 버스 번호, 도시철도 호선, 도선명 |
| `startStationId` | string/null | X | 승차 정류장/역 외부 ID |
| `startStationName` | string/null | X | 승차 정류장/역 이름 |
| `endStationId` | string/null | X | 하차 정류장/역 외부 ID |
| `endStationName` | string/null | X | 하차 정류장/역 이름 |
| `instruction` | string | O | 사용자에게 보여줄 구간 안내문 |
| `durationMinutes` | integer | O | 구간 소요시간(분) |
| `distanceMeters` | integer/null | X | 구간 거리(m) |
| `stationCount` | integer/null | X | 경유 정류장/역 수 |
| `waitMinutes` | integer | O | 해당 구간 탑승 전 대기시간 |
| `realtimeStatus` | string | O | `AVAILABLE`, `PARTIAL`, `UNAVAILABLE` |

## 전환 후 지도 응답 추가 필드

`GET /api/v1/schedules/{scheduleId}/map`은 기존 좌표 중심 응답을 유지하되, 화면에서 일정 타임라인과 마커를 함께 설명할 수 있도록 다음 필드를 추가한다.

| 위치 | 필드 | 자료형 | 필수 | 의미 |
| --- | --- | --- | :---: | --- |
| `markers[]` | `arriveAt` | string(time) | O | 장소 도착 예정시각 |
| `markers[]` | `departAt` | string(time) | O | 장소 출발 예정시각 |
| `markers[]` | `subtitle` | string | X | 카테고리, 체류시간 등 마커 보조 문구 |
| `markers[]` | `riskLevel` | string | O | `NORMAL`, `NOTICE`, `WARNING` |
| `routeLines[]` | `durationMinutes` | integer | X | 선 조각 소요시간 |
| `routeLines[]` | `distanceMeters` | integer/null | X | 선 조각 거리 |
| `routeLines[]` | `instruction` | string | X | 지도 선 조각 안내문 |
| `routeLines[]` | `fallbackUsed` | boolean | O | fallback 좌표 여부 |

## 전환 후 컬럼 설명

### `schedule_stops` 추가 컬럼

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `arrive_at` | time | O | 방문 장소 도착 예정시각 |
| `depart_at` | time | O | 방문 장소 출발 예정시각 |
| `selection_reasons_json` | json/text | O | 장소 선택 이유 문자열 배열 |
| `warnings_json` | json/text | O | 방문지 주의사항 문자열 배열 |

### `transit_routes` 추가 컬럼

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `origin_name` | varchar | O | 경로 출발 지점명 |
| `destination_name` | varchar | O | 경로 도착 지점명 |
| `summary` | varchar/text | O | 사용자용 이동 요약 |
| `depart_at` | time | O | 이동 시작 예정시각 |
| `arrive_at` | time | O | 이동 종료 예정시각 |
| `walk_minutes` | integer | O | 도보 이동 합계(분) |
| `wait_minutes` | integer | O | 대기시간 합계(분) |
| `transfer_count` | integer | O | 환승 횟수 |
| `provider` | varchar | O | 경로 산출 출처. 예: `ODSAY`, `INTERNAL_WALK`, `FAKE`, `FALLBACK` |
| `realtime_status` | varchar | O | 실시간 정보 반영 상태. `AVAILABLE`, `PARTIAL`, `UNAVAILABLE` |
| `fallback_used` | boolean | O | 외부 API 실패 후 fallback 사용 여부 |
| `warnings_json` | json/text | O | 경로 주의사항 문자열 배열 |

### `transit_segments` 추가 컬럼

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `start_station_id` | varchar | X | 승차 정류장/역 외부 ID |
| `end_station_id` | varchar | X | 하차 정류장/역 외부 ID |
| `instruction` | varchar/text | O | 사용자용 구간 안내문 |
| `duration_minutes` | integer | O | 구간 소요시간(분) |
| `distance_meters` | integer | X | 구간 거리(m) |
| `station_count` | integer | X | 경유 정류장/역 수 |
| `wait_minutes` | integer | O | 해당 구간 전 대기시간 |
| `realtime_status` | varchar | O | 실시간 정보 반영 상태 |

### `transit_route_lines` 추가 컬럼

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `duration_minutes` | integer | X | 지도 선 조각 소요시간 |
| `distance_meters` | integer | X | 지도 선 조각 거리 |
| `instruction` | varchar/text | X | 지도에서 보여줄 선 조각 안내문 |
| `fallback_used` | boolean | O | TMAP/ODsay 상세 좌표 실패 후 직선 또는 단순 좌표 사용 여부 |

## 변환 구현 순서

1. `TransitRouteResult.Segment`에 상세 필드를 추가하고 ODsay `subPath` 파싱을 확장한다.
2. `TransitRoute`, `TransitSegment`, `TransitRouteLine`, `ScheduleStop` 엔티티와 ERD를 확장한다.
3. `ScheduleService`에서 하루 시작시각부터 이동/체류를 누적해 `arriveAt`, `departAt`을 계산한다.
4. 사용자 답변 기반 장소 선택 이유와 경고 문구를 생성한다.
5. `ScheduleResponse`, `ScheduleMapResponse`를 상세 일정 전달용으로 확장한다.
6. `docs/API_SPEC.md`, `docs/API_FIELD_GUIDE.md`, `docs/ERD.md`, `docs/api-change-log.md`를 실제 계약으로 갱신한다.
7. 기존 일정 생성 테스트에 응답 필드 검증을 추가하고, 예제 브라우저를 새 응답 구조로 갱신한다.
