# 부산 여행 일정 서비스 1차 스프린트 ERD

## 표기

| 표기 | 의미 |
| --- | --- |
| PK | 행을 구분하는 기본키 |
| FK | 다른 테이블을 참조하는 외래키 |
| UK | 중복을 허용하지 않는 고유키 |
| O | 필수값 |
| X | `null` 허용 |

로컬·개발 환경은 PostgreSQL을 사용하고 테스트는 H2를 사용한다. migration 도구가 확정되기 전까지 `json`, `datetime`, UUID의 물리 타입은 확정하지 않으며 아래 논리 모델을 기준으로 Entity를 설계한다.

## 1. questions

일정 생성 전에 보여줄 질문을 저장한다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | varchar | PK, O | 질문 고정 문자열 ID. 예: `COMPANION` |
| `text` | varchar | O | 사용자에게 표시할 질문 |
| `type` | varchar | O | 선택 방식. 1차는 `SINGLE_CHOICE` |
| `required` | boolean | O | 필수 응답 여부 |
| `display_order` | integer | O | 질문 표시 순서 |
| `active` | boolean | O | 현재 사용 여부 |

## 2. answers

질문별 선택 답변을 저장한다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | varchar | PK, O | 답변 고정 문자열 ID. 예: `COMPANION_PARENTS` |
| `question_id` | varchar | FK, O | 답변이 속한 `questions.id` |
| `label` | varchar | O | 답변 버튼 문구 |
| `display_order` | integer | O | 질문 안의 표시 순서 |
| `active` | boolean | O | 현재 사용 여부 |

## 3. places

TourAPI에서 수집한 관광지·음식점·문화시설 등의 기본정보다. 장소 검색, 필수 방문 장소 선택, 일정 교체의 기준이다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | bigint | PK, O | 서비스 내부 장소 ID |
| `source` | varchar | UK 일부, O | 데이터 출처. `TOUR_API` |
| `external_content_id` | varchar | UK 일부, O | TourAPI 콘텐츠 ID |
| `content_type_id` | varchar | X | TourAPI 관광 타입 ID |
| `name` | varchar | O | 장소명 |
| `category` | varchar | X | 화면 표시용 분류 |
| `address` | varchar | X | 주소 |
| `longitude` | decimal | O | 경도 |
| `latitude` | decimal | O | 위도 |
| `primary_image_url` | text | X | 대표 이미지 URL |
| `created_at` | datetime | O | 최초 적재시각 |
| `updated_at` | datetime | O | 마지막 갱신시각 |

`source + external_content_id` 조합은 중복될 수 없다. 이름 검색과 위치 검색을 위한 인덱스는 DBMS 확정 후 migration에서 정의한다.

## 4. place_details

장소의 상세 설명과 홈페이지를 저장한다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `place_id` | bigint | PK, FK, O | 상세정보가 속한 `places.id` |
| `overview` | text | X | 장소 상세 설명 |
| `homepage` | text | X | 홈페이지 |
| `raw_json` | json | X | TourAPI 상세 원본 응답 |

## 5. place_operating_infos

장소 운영시간, 휴무일, 요금, 주차 정보를 원문 중심으로 저장한다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `place_id` | bigint | PK, FK, O | 운영정보가 속한 `places.id` |
| `opening_hours_text` | text | X | 운영시간 원문 |
| `closed_days_text` | text | X | 휴무일 원문 |
| `use_fee_text` | text | X | 이용요금 원문 |
| `parking_text` | text | X | 주차 안내 원문 |
| `requires_manual_check` | boolean | O | 원문 직접 확인 필요 여부 |
| `raw_json` | json | X | TourAPI 운영정보 원본 응답 |

## 6. place_images

장소별 이미지 목록을 저장한다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | bigint | PK, O | 내부 이미지 ID |
| `place_id` | bigint | FK, O | 이미지가 속한 `places.id` |
| `url` | text | O | 원본 이미지 URL |
| `thumbnail_url` | text | X | 썸네일 URL |
| `copyright_type` | varchar | X | TourAPI 저작권 구분 |
| `display_order` | integer | O | 이미지 표시 순서 |

## 7. schedules

여행 일정 전체의 조건과 상태를 저장한다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | uuid | PK, O | 일정 ID |
| `status` | varchar | O | 일정 상태. 예: `CONFIRMED` |
| `start_date` | date | O | 여행 시작일 |
| `end_date` | date | O | 여행 종료일 |
| `daily_start_time` | time | O | 기본 일일 시작시각 |
| `daily_end_time` | time | O | 기본 일일 종료시각 |
| `start_place_name` | varchar | O | 출발지명 |
| `start_longitude` | decimal | O | 출발지 경도 |
| `start_latitude` | decimal | O | 출발지 위도 |
| `end_place_name` | varchar | O | 최종 도착지명 |
| `end_longitude` | decimal | O | 도착지 경도 |
| `end_latitude` | decimal | O | 도착지 위도 |
| `style_summary` | text | X | 질문 답변 기반 여행 스타일 요약 |
| `condition_json` | json | O | 일정 생성 요청 조건 스냅샷 |
| `created_at` | datetime | O | 생성시각 |
| `updated_at` | datetime | O | 마지막 수정시각 |

`condition_json` 예시:

```json
{
  "selectedAnswers": [
    {
      "questionId": "COMPANION",
      "answerId": "COMPANION_PARENTS"
    }
  ],
  "mustVisitPlaceIds": [
    101,
    205
  ],
  "days": [
    {
      "dayNo": 1,
      "startTime": "09:00",
      "endTime": "19:00",
      "startLocation": {"name": "부산역", "longitude": 129.0403, "latitude": 35.1151},
      "endLocation": {"name": "해운대 숙소", "longitude": 129.158, "latitude": 35.159}
    }
  ]
}
```

일정 수정용 `version`, 편집 토큰, 사용자 ID는 1차 스프린트에 두지 않는다.

## 8. schedule_days

다일 일정의 하루를 저장한다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | uuid | PK, O | 하루 일정 ID |
| `schedule_id` | uuid | FK, O | 소속 `schedules.id` |
| `day_no` | integer | UK 일부, O | 여행 일차 |
| `date` | date | O | 실제 날짜 |
| `start_time` | time | O | 해당 일차 일정 시작시각 |
| `end_time` | time | O | 해당 일차 일정 종료 목표시각 |
| `start_place_name` | varchar | O | 해당 일차 출발지명 |
| `start_longitude` | decimal | O | 해당 일차 출발지 경도 |
| `start_latitude` | decimal | O | 해당 일차 출발지 위도 |
| `end_place_name` | varchar | O | 해당 일차 도착지명 |
| `end_longitude` | decimal | O | 해당 일차 도착지 경도 |
| `end_latitude` | decimal | O | 해당 일차 도착지 위도 |

하나의 일정에서 `schedule_id + day_no`는 중복될 수 없다.

## 9. schedule_stops

특정 날짜에 특정 장소를 방문하는 계획이다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | uuid | PK, O | 방문 계획 ID |
| `schedule_day_id` | uuid | FK, O | 소속 `schedule_days.id` |
| `place_id` | bigint | FK, O | 방문할 `places.id` |
| `stop_order` | integer | UK 일부, O | 해당 날짜 안의 방문 순서 |
| `stay_minutes` | integer | O | 체류시간(분) |
| `selection_reasons_json` | json | O | 장소 선택 이유 문자열 배열 |
| `warnings_json` | json | O | 방문지 주의사항 문자열 배열 |

하루 안에서 `schedule_day_id + stop_order`는 중복될 수 없다. `mustVisitPlaceIds`로 전달된 장소는 생성 결과의 방문 계획에 포함해야 한다.

## 10. transit_routes

하루 일정 안에서 장소로 들어오는 경로와 마지막 장소에서 최종 도착지로 가는 경로를 저장한다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | uuid | PK, O | 경로 ID |
| `schedule_day_id` | uuid | FK, O | 경로가 속한 `schedule_days.id` |
| `schedule_stop_id` | uuid | FK, UK, X | `INBOUND` 경로가 도착하는 `schedule_stops.id` |
| `route_type` | varchar | O | `INBOUND` 또는 `FINAL` |
| `route_order` | integer | UK 일부, O | 해당 날짜에서 경로를 표시할 순서 |
| `total_minutes` | integer | O | 전체 이동시간(분) |
| `fare_amount` | integer | X | 예상 대중교통 요금(원) |
| `provider` | varchar | O | 경로 산출 출처. 예: `ODSAY`, `INTERNAL_WALK`, `FAKE`, `UNKNOWN` |
| `realtime_status` | varchar | O | 실시간 정보 반영 상태. `AVAILABLE`, `PARTIAL`, `UNAVAILABLE` |
| `fallback_used` | boolean | O | 외부 API 실패 후 fallback 경로 사용 여부 |
| `warnings_json` | json | O | 경로 주의사항 문자열 배열 |
| `raw_json` | json | X | ODsay 원본 응답 |

- `INBOUND`는 이전 지점에서 `schedule_stop_id` 장소로 들어오는 경로다.
- `FINAL`은 마지막 방문 장소에서 일정의 최종 도착지로 가는 경로이며 `schedule_stop_id`는 `null`이다.
- 방문 계획 하나에는 `INBOUND` 경로가 최대 하나다.
- 하루에는 `FINAL` 경로가 최대 하나다.
- `schedule_day_id + route_order`는 중복될 수 없다.

## 11. transit_segments

전체 대중교통 경로를 도보·버스·도시철도 구간으로 나누어 저장한다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | uuid | PK, O | 구간 ID |
| `transit_route_id` | uuid | FK, O | 소속 `transit_routes.id` |
| `segment_order` | integer | UK 일부, O | 경로 안의 구간 순서 |
| `mode` | varchar | O | `WALK`, `BUS`, `SUBWAY` |
| `line_name` | varchar | X | 버스 번호 또는 도시철도 호선 |
| `start_station_id` | varchar | X | 승차 정류장 또는 출발역 외부 ID |
| `start_station_name` | varchar | X | 승차 정류장 또는 출발역 |
| `end_station_id` | varchar | X | 하차 정류장 또는 도착역 외부 ID |
| `end_station_name` | varchar | X | 하차 정류장 또는 도착역 |
| `instruction` | text | O | 사용자용 구간 안내문 |
| `duration_minutes` | integer | O | 구간 소요시간(분). 원천 데이터가 없으면 `0` 또는 경로 단일 구간 총시간 |
| `distance_meters` | integer | X | 구간 거리(m) |
| `station_count` | integer | X | 경유 정류장 또는 역 수 |
| `wait_minutes` | integer | O | 해당 구간 탑승 전 대기시간 |
| `realtime_status` | varchar | O | 실시간 정보 반영 상태 |

## 12. transit_route_lines

지도에서 대중교통 경로선을 그리는 좌표 조각을 저장한다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | uuid | PK, O | 경로선 ID |
| `transit_route_id` | uuid | FK, O | 소속 `transit_routes.id` |
| `line_order` | integer | UK 일부, O | 경로선을 그릴 순서 |
| `mode` | varchar | O | 경로선의 이동수단 |
| `line_name` | varchar | X | 버스 번호 또는 도시철도 호선 |
| `coordinates_json` | json | O | `[경도, 위도]` 좌표 배열 |
| `duration_minutes` | integer | X | 지도 선 조각 소요시간 |
| `distance_meters` | integer | X | 지도 선 조각 거리(m) |
| `instruction` | text | X | 지도에서 보여줄 선 조각 안내문 |
| `fallback_used` | boolean | O | fallback 좌표 사용 여부 |

## 13. share_links

일정을 읽기 전용으로 공유하기 위한 링크다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | uuid | PK, O | 공유 링크 관리 ID |
| `schedule_id` | uuid | FK, O | 공유 대상 `schedules.id` |
| `token_hash` | varchar(64) | UK, O | 32바이트 난수 토큰의 SHA-256 hex 해시 |
| `expires_at` | datetime | X | 만료시각 |
| `revoked_at` | datetime | X | 폐기시각 |
| `created_at` | datetime | O | 생성시각 |

## 관계

| 관계 | 의미 |
| --- | --- |
| `questions` 1 : N `answers` | 질문 하나는 여러 답변 선택지를 가진다 |
| `places` 1 : 0..1 `place_details` | 장소는 상세정보를 가질 수 있다 |
| `places` 1 : 0..1 `place_operating_infos` | 장소는 운영정보를 가질 수 있다 |
| `places` 1 : N `place_images` | 장소는 여러 이미지를 가진다 |
| `schedules` 1 : N `schedule_days` | 일정은 여러 날짜를 가진다 |
| `schedule_days` 1 : N `schedule_stops` | 하루 일정은 여러 방문 계획을 가진다 |
| `places` 1 : N `schedule_stops` | 장소는 여러 일정에서 방문될 수 있다 |
| `schedule_days` 1 : N `transit_routes` | 하루 일정은 방문 경로와 최종 도착 경로를 가진다 |
| `schedule_stops` 1 : 0..1 `transit_routes` | 방문 계획은 `INBOUND` 경로를 최대 하나 가진다 |
| `transit_routes` 1 : N `transit_segments` | 경로는 여러 교통 구간으로 나뉜다 |
| `transit_routes` 1 : N `transit_route_lines` | 경로는 여러 지도 선 조각을 가진다 |
| `schedules` 1 : N `share_links` | 일정은 여러 공유 링크를 만들 수 있다 |

## DB에 저장하지 않는 데이터

| 데이터 | 이유 |
| --- | --- |
| 출발지·도착지 검색 결과 전체 | 사용자가 선택한 이름과 좌표만 일정에 저장 |
| 장소 검색의 `distanceMeters` | 검색 중심에 따라 매번 달라짐 |
| 주변 편의시설 결과 | Kakao Local API 실시간 조회 데이터 |
| PDF·이미지 파일 | 프론트엔드 브라우저에서 생성 |

## 일정 수정 정책

```text
PATCH /schedules/{scheduleId}
→ 요청받은 stops를 현재 일정에 바로 반영
→ 추가, 삭제, 일차, 순서, 체류시간 변경
→ 영향을 받는 대중교통 경로 재계산
→ 저장
```

`version`을 사용하지 않으므로 동시에 여러 수정이 발생하면 마지막 저장 결과가 최종 상태가 된다.
