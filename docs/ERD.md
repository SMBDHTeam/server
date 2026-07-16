# 부산 여행 일정 서비스 ERD

> 구현 상태 안내
>
> - 1~13번 테이블과 `일정 생성 V2 변경`은 현재 구현 기준이다.
> - V2 컬럼과 테이블은 `V4__schedule_generation_v2.sql`, 질문 화면 단계는 `V5__add_question_ui_step.sql`에서 추가한다.

## 표기

| 표기 | 의미 |
| --- | --- |
| PK | 행을 구분하는 기본키 |
| FK | 다른 테이블을 참조하는 외래키 |
| UK | 중복을 허용하지 않는 고유키 |
| O | 필수값 |
| X | `null` 허용 |

로컬·개발 환경은 PostgreSQL과 Flyway migration을 사용하고 테스트는 H2를 사용한다. 아래 논리 모델을 기준으로 Entity와 migration을 함께 관리하며, 공유된 migration은 수정하지 않고 새 버전을 추가한다.

## 1. questions

일정 생성 전에 보여줄 질문을 저장한다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | varchar | PK, O | 질문 고정 문자열 ID. 예: `COMPANION` |
| `text` | varchar | O | 사용자에게 표시할 질문 |
| `type` | varchar | O | `SINGLE_CHOICE`, `MULTIPLE_CHOICE` |
| `required` | boolean | O | 필수 응답 여부 |
| `min_selections` | integer | O | 최소 답변 선택 수 |
| `max_selections` | integer | O | 최대 답변 선택 수 |
| `ui_step` | integer | O | 기본 생성 화면에서 질문을 표시할 단계. `1~3` |
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
| `source` | varchar | UK 일부, O | 데이터 출처. `TOUR_API`, `KAKAO_LOCAL` |
| `external_content_id` | varchar | UK 일부, O | 원천 시스템의 장소 ID |
| `content_type_id` | varchar | X | TourAPI 관광 타입 ID |
| `name` | varchar | O | 장소명 |
| `category` | varchar | X | 화면 표시용 분류 |
| `address` | varchar | X | 주소 |
| `longitude` | decimal | O | 경도 |
| `latitude` | decimal | O | 위도 |
| `primary_image_url` | text | X | 대표 이미지 URL |
| `place_url` | text | X | 외부 장소 페이지 URL |
| `created_at` | datetime | O | 최초 적재시각 |
| `updated_at` | datetime | O | 마지막 갱신시각 |
| `source_modified_at` | datetime | X | TourAPI 목록의 마지막 수정시각 |
| `last_seen_at` | datetime | O | 마지막 목록 발견시각 |
| `last_synced_at` | datetime | X | 상세·소개·이미지 동기화 성공시각 |
| `ingestion_status` | varchar | O | `PENDING`, `SYNCED`, `FAILED` |
| `ingestion_retry_count` | integer | O | 연속 상세 동기화 실패 횟수 |
| `ingestion_last_error` | text | X | 비밀값을 제외한 마지막 내부 오류 코드 |
| `ingestion_next_retry_at` | datetime | X | 다음 상세 동기화 재시도 가능시각 |

`source + external_content_id` 조합은 중복될 수 없다. 이름 검색과 위치 검색을 위한 인덱스는 DBMS 확정 후 migration에서 정의한다.

## 4. place_details

장소의 상세 설명과 홈페이지를 저장한다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `place_id` | bigint | PK, FK, O | 상세정보가 속한 `places.id` |
| `overview` | text | X | 장소 상세 설명 |
| `homepage` | text | X | 홈페이지 |
| `raw_json` | text | X | TourAPI 상세 원본 JSON 문자열 |

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
| `raw_json` | text | X | TourAPI 운영정보 원본 JSON 문자열 |

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

### 6.1 place_ingestion_locks

여러 서버 인스턴스나 중복 스케줄 실행이 동시에 장소 동기화를 수행하지 않도록 임대 방식으로 잠근다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `lock_name` | varchar | PK, O | 적재 작업 고정 이름 |
| `locked_by` | varchar | O | 현재 임대 소유자 식별값 |
| `locked_until` | datetime | O | 장애 시 자동 만료되는 임대 종료시각 |

### 6.2 tour_api_request_usage

TourAPI의 일일 요청 제한을 서버 재시작과 중복 실행 이후에도 지키기 위한 날짜별 예약 사용량이다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `usage_date` | date | PK, O | KST 기준 요청 사용일 |
| `requests_used` | integer | O | 해당 날짜에 예약한 요청 수 |
| `updated_at` | datetime | O | 마지막 요청 예약시각 |

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

## 일정 생성 V2 변경

### V2-1. 기존 테이블 변경

#### `questions`

| 변경 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `min_selections` | integer | O | 최소 답변 선택 수. 기본 `1` |
| `max_selections` | integer | O | 최대 답변 선택 수 |
| `ui_step` | integer | O | 기본 생성 화면 질문 그룹. 기본 `1` |

`ui_step`은 `V5__add_question_ui_step.sql`에서 추가한다.

- `type` 허용값에 `MULTIPLE_CHOICE`를 추가한다.
- `SINGLE_CHOICE`는 `max_selections=1`이어야 한다.
- 모든 질문은 `0 <= min_selections <= max_selections`를 만족해야 한다.
- `required=true`이면 `min_selections >= 1`이어야 한다.
- `ui_step`은 현재 기본 생성 화면의 `1~3` 중 하나이며 프론트는 배열 위치 대신 이 값을 사용한다.

#### `places`

| 변경 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `place_url` | text | X | Kakao 등 외부 장소 페이지 URL |

- `source` 허용값에 `KAKAO_LOCAL`을 추가한다.
- 기존 `(source, external_content_id)` 고유 조건을 그대로 사용한다.
- Kakao 장소는 `content_type_id`, 상세정보, 운영정보, 이미지가 없을 수 있다.
- Kakao 장소도 `last_seen_at`은 Resolve 시각, `ingestion_status`는 `SYNCED`로 저장해 기존 NOT NULL 제약을 만족시킨다.
- 검색 결과 전체는 저장하지 않고 `POST /places/resolve`로 선택된 장소만 저장한다.

#### `schedules`

| 변경 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `preview_id` | uuid | FK, UK, V2 O, legacy X | 생성에 사용한 `schedule_previews.id` |
| `time_zone` | varchar | V2 O, legacy X | 일정 시간대. 부산 서비스는 `Asia/Seoul` |
| `lodging_mode` | varchar | V2 O, legacy X | `UNDECIDED`, `FIXED_BASE`, `PER_NIGHT` |
| `route_coverage` | varchar | V2 O, legacy X | `FULL`, `ATTRACTION_ROUTES_ONLY` |
| `planning_warnings_json` | json | V2 O, legacy X | Planner 가정 경고 코드 배열 |

V2에서 다음 기존 컬럼은 호환 migration 기간에 nullable legacy 컬럼으로 전환한 뒤 제거를 검토한다.

| legacy 컬럼 | V2 처리 |
| --- | --- |
| `daily_start_time` | 일차별 기준은 `schedule_days.start_time`; V2는 호환값만 저장하고 응답에서 생략 |
| `daily_end_time` | 일차별 기준은 `schedule_days.end_time`; V2는 호환값만 저장하고 응답에서 생략 |
| `end_place_name`, `end_longitude`, `end_latitude` | 종료 제약이 없을 수 있으므로 nullable |

사용자 원문과 정규화 결과의 기준 스냅샷은 `schedule_previews`에 보존한다. `condition_json`은 기존 조회 호환을 위한 질문·필수 장소 요약만 저장한다.

#### `schedule_days`

숙소 미정 일정은 일차 출발·도착 위치가 없을 수 있으므로 위치 컬럼을 nullable로 전환한다.

| 변경 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `start_place_name` | varchar | X | 확정된 일차 출발지명 |
| `start_longitude` | decimal | X | 확정된 일차 출발지 경도 |
| `start_latitude` | decimal | X | 확정된 일차 출발지 위도 |
| `end_place_name` | varchar | X | 확정된 일차 도착지명 |
| `end_longitude` | decimal | X | 확정된 일차 도착지 경도 |
| `end_latitude` | decimal | X | 확정된 일차 도착지 위도 |
| `start_location_source` | varchar | O | `USER`, `LODGING`, `DAY_OVERRIDE`, `PLANNER_DECIDES` |
| `end_location_source` | varchar | O | `LODGING`, `END_CONSTRAINT`, `DAY_OVERRIDE`, `PLANNER_DECIDES` |

위치명과 좌표 세 필드는 모두 존재하거나 모두 `null`이어야 한다. `PLANNER_DECIDES`이면서 숙소 이동을 계산하지 않은 경우 `null`을 허용한다.

#### `schedule_stops`

| 변경 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `fixed_starts_at` | datetime | X | 고정 행사 시작시각 |
| `fixed_ends_at` | datetime | X | 고정 행사 종료시각 |

두 필드는 일반 방문지에서는 `null`이고 고정 행사 방문지에서는 함께 저장한다. 상세 행사 식별자와 이름은 `schedule_fixed_events`에 저장한다.

### V2-2. schedule_previews

사용자 입력과 서버가 확정한 Planner 실행 조건을 저장한다. Preview는 생성 후 수정하지 않는다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | uuid | PK, O | Preview ID |
| `status` | varchar | O | `READY`, `REQUIRES_ACTION`, `EXPIRED`, `CONSUMED` |
| `start_date` | date | O | 여행 시작일 |
| `end_date` | date | O | 여행 종료일 |
| `input_json` | json | O | 사용자 입력 스냅샷 |
| `resolved_days_json` | json | O | 서버가 확정한 일차별 실행 조건 |
| `resolved_end_constraint_json` | json | X | 종료 제약과 적용 여유시간 |
| `applied_defaults_json` | json | O | 적용 기본값과 이유 |
| `interpreted_prompt_json` | json | O | 자유 요청 정규화 결과 |
| `warnings_json` | json | O | 확인 가능한 경고 목록 |
| `conflicts_json` | json | O | 해결해야 하는 충돌 목록 |
| `time_zone` | varchar | O | 여행지 시간대 |
| `lodging_mode` | varchar | O | 적용 숙소 모드 |
| `route_coverage` | varchar | O | 예상 경로 평가 범위 |
| `expires_at` | datetime | O | Preview 만료시각 |
| `consumed_at` | datetime | X | 일정 생성에 사용된 시각 |
| `created_at` | datetime | O | Preview 생성시각 |

- `status=CONSUMED`이면 `consumed_at`이 필수다.
- `schedules.preview_id`의 unique FK 하나로 Preview와 일정의 일대일 관계를 표현하며 반대 방향 FK를 중복해서 두지 않는다.
- 소비된 Preview는 일정과 함께 보존하고, 만료된 미소비 Preview는 만료 24시간 후 배치 정리한다.
- JSON에는 API Key와 인증 헤더를 저장하지 않는다. `customPrompt`는 로그에 남기지 않고 보존 정책을 적용한다.

### V2-3. schedule_creation_requests

일정 생성의 멱등성을 보장하고 최초 응답을 재생한다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | uuid | PK, O | 생성 요청 행 ID |
| `idempotency_key` | varchar(128) | UK, O | 클라이언트가 전달한 멱등성 키 |
| `request_hash` | varchar(64) | O | Preview ID의 SHA-256 해시 |
| `preview_id` | uuid | FK, O | 실행 대상 Preview |
| `status` | varchar | O | `IN_PROGRESS`, `COMPLETED`, `FAILED` |
| `schedule_id` | uuid | FK, X | 생성된 일정 ID |
| `response_status` | integer | X | 최초 성공 응답 상태 코드 |
| `response_json` | json | X | 동일 요청에 재생할 최초 성공 응답 |
| `last_error_code` | varchar | X | 마지막 실패 오류 코드 |
| `created_at` | datetime | O | 요청 최초 수신시각 |
| `completed_at` | datetime | X | 생성 완료시각 |
| `expires_at` | datetime | O | 멱등성 기록 보존 종료시각 |

- 같은 `idempotency_key`와 다른 `request_hash` 조합은 허용하지 않는다.
- `COMPLETED`이면 `schedule_id`, `response_status`, `response_json`, `completed_at`이 필수다.
- 동일 요청 재시도는 저장된 `response_json`을 재생한다.
- 생성 중 동일 키 요청은 기존 작업 완료를 최대 5초 기다리고, 계속 진행 중이면 `SCHEDULE_CREATION_IN_PROGRESS`를 반환한다.
- 생성 요청 기록은 최소 24시간 보존한 뒤 매일 04:30 정리한다.

### V2-4. schedule_fixed_events

Preview의 고정 행사 제약이 실제 일정의 방문지로 배치된 결과를 저장한다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | uuid | PK, O | 고정 행사 ID |
| `schedule_id` | uuid | FK, O | 소속 일정 |
| `schedule_stop_id` | uuid | FK, UK, O | 행사로 배치된 방문 계획 |
| `client_event_id` | varchar | O | Preview 요청의 행사 식별자 |
| `name` | varchar | O | 행사·공연명 |
| `starts_at` | datetime | O | 오프셋을 정규화한 행사 시작시각 |
| `ends_at` | datetime | O | 오프셋을 정규화한 행사 종료시각 |
| `created_at` | datetime | O | 저장시각 |

- `starts_at < ends_at`이어야 한다.
- 하나의 `schedule_stop`에는 고정 행사가 최대 하나다.
- 고정 행사 시간은 일반 일정 수정으로 변경하지 않는다.
- 행사 시간 변경은 새 Preview 기반 재생성 범위로 처리한다.

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
| `schedule_previews` 1 : 0..1 `schedules` | Preview는 일정 생성에 최대 한 번 소비된다 |
| `schedule_previews` 1 : N `schedule_creation_requests` | 한 Preview의 생성 시도와 멱등성 상태를 기록한다 |
| `schedules` 1 : N `schedule_fixed_events` | 일정은 여러 고정 행사를 포함할 수 있다 |
| `schedule_stops` 1 : 0..1 `schedule_fixed_events` | 방문 계획은 고정 행사와 최대 하나 연결된다 |

## DB에 저장하지 않는 데이터

| 데이터 | 이유 |
| --- | --- |
| 출발지·도착지 검색 결과 전체 | 사용자가 선택한 이름과 좌표만 일정에 저장 |
| 장소 검색의 `distanceMeters` | 검색 중심에 따라 매번 달라짐 |
| 주변 편의시설 결과 | Kakao Local API 실시간 조회 데이터 |
| PDF·이미지 파일 | 프론트엔드 브라우저에서 생성 |
| 통합 장소 검색의 미선택 Kakao 후보 | 사용자가 Resolve한 장소만 `places`에 저장 |

## 일정 수정 정책

```text
PATCH /schedules/{scheduleId}
→ 요청받은 stops를 현재 일정에 바로 반영
→ 추가, 삭제, 일차, 순서, 체류시간 변경
→ 영향을 받는 대중교통 경로 재계산
→ 저장
```

`version`을 사용하지 않으므로 동시에 여러 수정이 발생하면 마지막 저장 결과가 최종 상태가 된다.
