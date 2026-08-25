# 부산 여행 일정 서비스 ERD

> 구현 상태 안내
>
> - 1~26번 테이블과 `일정 생성 V2 변경`은 현재 구현 기준이다.
> - V2 컬럼과 테이블은 `V4__schedule_generation_v2.sql`, 질문 화면 단계는 `V5__add_question_ui_step.sql`에서 추가한다.
> - 사용자(14번)는 `V6__create_users_table.sql`, 커뮤니티(15~26번)는 `V8__create_community_tables.sql`에서 추가한다.

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
| `user_id` | bigint | FK, X | 소유자 `users.id`. 인증 도입 전 일정은 NULL 이며 목록에 나오지 않는다 |
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
| `arrive_at` | time | X | 도착 시각. 날짜는 소속 `schedule_days.date`로 결정된다 |
| `depart_at` | time | X | 출발 시각 |
| `fixed_starts_at` | timestamptz | X | 고정 행사 시작. 고정 행사가 아닌 방문지는 `null` |
| `fixed_ends_at` | timestamptz | X | 고정 행사 종료 |
| `selection_reasons_json` | json | O | 장소 선택 이유 문자열 배열 |
| `warnings_json` | json | O | 방문지 주의사항 문자열 배열 |

`arrive_at`과 `depart_at`은 V7 이전에 저장하지 않았다. 그 이전에 만들어진 방문지는 `null`이며 값을 복원할 수 없다.

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

## 14. users

서비스 사용자다. V6에서 만들고 V9에서 인증·권한 컬럼을 더했다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | bigint | PK, O | 사용자 ID |
| `nickname` | varchar(255) | O | 표시 이름. 사용자가 직접 바꾼다 |
| `profile_image_url` | text | X | 프로필 사진 |
| `email` | varchar(255) | X | 제공자가 준 이메일. 식별자로 쓰지 않는다 |
| `provider` | varchar(20) | X | 로그인 제공자. 현재 `GOOGLE` |
| `provider_id` | varchar(255) | X | 제공자 고유 ID. 구글은 `sub` |
| `role` | varchar(20) | O | `USER` 또는 `ADMIN`. 기본 `USER` |
| `status` | varchar(20) | O | `ACTIVE`·`SUSPENDED`·`WITHDRAWN`. 기본 `ACTIVE` |
| `suspended_until` | datetime | X | 정지 만료시각. 지나면 스스로 풀린 것으로 본다 |
| `suspended_reason` | text | X | 정지 사유 |
| `created_at` | datetime | O | 가입시각 |
| `deleted_at` | datetime | X | 탈퇴시각 |

인덱스는 다음과 같다.

| 이름 | 대상 | 비고 |
| --- | --- | --- |
| `uk_users_nickname_active` | (`nickname`) | 부분 고유. `deleted_at IS NULL` 인 행만 |
| `uk_users_provider_active` | (`provider`, `provider_id`) | 부분 고유. 같은 계정의 중복 가입을 막는다 |
| `idx_users_role` | (`role`) | 관리자 목록 조회 |

**`provider`·`provider_id`·`email`은 nullable이다.** V6로 이미 만들어진 행이 있어
`NOT NULL`을 걸 수 없다. 로그인으로 생기는 행은 애플리케이션이 항상 채운다.

**고유 인덱스를 탈퇴하지 않은 행에만 적용한다.** 닉네임과 같은 이유다. 탈퇴 후 재가입은
새 행으로 들어오며, 이전 행은 이력으로 남는다.

**정지 만료에 배치를 두지 않는다.** `suspended_until`이 지났는지를 읽는 쪽에서 판단한다
(`User.isWriteBlockedAt`). 상태를 되돌리는 스케줄러가 없어도 만료가 동작한다.

## 15. posts

커뮤니티 게시물이다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | bigint | PK, O | 게시물 ID |
| `user_id` | bigint | FK, O | 작성자 `users.id` |
| `content` | text | O | 본문. 해시태그를 본문 안에 함께 적는다 |
| `like_count` | integer | O | 좋아요 수. 조회 성능용 집계 컬럼 |
| `comment_count` | integer | O | 댓글 수. 조회 성능용 집계 컬럼 |
| `created_at` | datetime | O | 작성시각 |
| `updated_at` | datetime | O | 마지막 수정시각 |
| `deleted_at` | datetime | X | 삭제 표시시각 |

`id`는 증가하는 정수다. 피드는 `id`를 커서로 쓰는 방식이라 순서가 있는 식별자가 필요하다.

집계 컬럼은 엔티티를 읽어 고쳐 쓰지 않고 `UPDATE ... SET like_count = like_count + 1`처럼
DB에서 직접 증감시킨다. 동시에 들어온 요청이 같은 값을 읽어 하나가 사라지는 것을 막기 위함이다.

삭제는 `deleted_at`만 남긴다. 삭제 후 30일간 `POST /posts/{postId}/restore` 로 되돌릴 수 있으므로 조회에서 제외하되
행은 보존한다. **만료된 게시물을 정리하는 배치는 아직 없다.**

인덱스: `idx_posts_created_at`, `idx_posts_user_id`

## 16. post_media

게시물에 첨부한 사진·영상이다. 게시물당 최소 한 건이 필요하며 요청 DTO에서 검증한다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | bigint | PK, O | 미디어 ID |
| `post_id` | bigint | FK, O | 소속 `posts.id` |
| `media_type` | varchar | O | `IMAGE`, `VIDEO` 둘 중 하나. 애플리케이션의 열거형으로 고정한다 |
| `url` | text | O | 업로드된 파일 URL |
| `sort_order` | integer | O | 게시물 안의 표시 순서 |

파일 업로드는 서버가 하지 않는다. 클라이언트가 저장소에 올린 뒤 URL만 전달한다.
**저장소 선정과 업로드 API는 미정이다.**

인덱스: `idx_post_media_post_id`

## 17. post_place_tags

게시물에 태그한 장소다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | bigint | PK, O | 태그 ID |
| `post_id` | bigint | FK, O | 소속 `posts.id` |
| `place_id` | bigint | FK, O | 태그한 `places.id` |
| `latitude` | decimal | X | 사진이 실제로 촬영된 위도 |
| `longitude` | decimal | X | 사진이 실제로 촬영된 경도 |

좌표는 장소 대표 좌표가 아니라 **사진 EXIF에서 얻은 촬영 지점**이다. `places`의 좌표를 복사하지
않으며, EXIF가 없으면 `null`로 둔다. 값의 유무로 촬영 위치를 아는지가 구분된다. 화면에 표시할
좌표가 없으면 `place_id`로 `places`에서 읽는다.

인덱스: `idx_post_place_tags_post_id`, `idx_post_place_tags_place_id`

## 18. comments

게시물 댓글이다. 최상위 댓글과 답글 두 단계만 표현한다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | bigint | PK, O | 댓글 ID |
| `post_id` | bigint | FK, O | 소속 `posts.id` |
| `user_id` | bigint | FK, O | 작성자 `users.id` |
| `parent_id` | bigint | FK, X | 답글이면 부모 `comments.id`, 일반 댓글이면 `null` |
| `content` | text | O | 내용 |
| `like_count` | integer | O | 좋아요 수. 조회 성능용 집계 컬럼 |
| `created_at` | datetime | O | 작성시각 |
| `updated_at` | datetime | O | 마지막 수정시각 |
| `deleted_at` | datetime | X | 삭제 표시시각 |

`parent_id`는 같은 테이블을 참조한다. 답글에 다시 답글을 다는 요청은 거절한다.

삭제된 댓글에 살아 있는 답글이 있으면 목록에서 자리를 유지하고 작성자와 내용만 감춘다.
부모가 사라지면 답글이 화면에서 함께 없어지기 때문이다. 답글이 없으면 목록에서 제외한다.

인덱스: `idx_comments_post_id`, `idx_comments_parent_id`

## 19. post_likes

게시물 좋아요다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `post_id` | bigint | PK 일부, FK, O | `posts.id` |
| `user_id` | bigint | PK 일부, FK, O | `users.id` |
| `created_at` | datetime | O | 누른 시각 |

대리키 없이 `(post_id, user_id)`가 기본키다. 같은 사람이 같은 게시물에 두 번 누르는 것을 DB가
막는다. 애플리케이션에서 확인하는 방식은 동시 요청에서 뚫린다.

키 순서는 게시물 기준 조회가 많아 `post_id`가 앞이다.

## 20. comment_likes

댓글 좋아요다. 구조와 이유는 `post_likes`와 같다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `comment_id` | bigint | PK 일부, FK, O | `comments.id` |
| `user_id` | bigint | PK 일부, FK, O | `users.id` |
| `created_at` | datetime | O | 누른 시각 |

## 21. bookmarks

게시물 저장이다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `user_id` | bigint | PK 일부, FK, O | `users.id` |
| `post_id` | bigint | PK 일부, FK, O | `posts.id` |
| `created_at` | datetime | O | 저장한 시각 |

`post_likes`와 달리 `user_id`가 키 앞에 온다. 주 용도가 "내 북마크 목록" 조회라 사용자 기준으로
읽는 일이 많기 때문이다. 이 순서 덕분에 별도 인덱스가 필요 없다.

몇 명이 저장했는지는 응답에 담지 않는다.

## 22. follows

팔로우 관계다. 방향이 있어 서로 팔로우하려면 두 행이 필요하다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `follower_id` | bigint | PK 일부, FK, O | 팔로우하는 `users.id` |
| `following_id` | bigint | PK 일부, FK, O | 팔로우당하는 `users.id` |
| `created_at` | datetime | O | 맺은 시각 |

한 테이블이 `users`를 두 번 참조한다. 팔로워 목록과 팔로잉 목록이 모두 필요해 역방향 인덱스를
둔다. 대리키가 없어 커서로 삼을 값이 없으므로 목록은 오프셋 페이징을 쓴다.

팔로워 수는 집계 컬럼 없이 매번 센다. 현재 규모에서는 충분하며, 느려지면 `users`에 컬럼을 추가한다.

인덱스: `idx_follows_following_id`

## 23. blocks

차단 관계다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `blocker_id` | bigint | PK 일부, FK, O | 차단한 `users.id` |
| `blocked_id` | bigint | PK 일부, FK, O | 차단당한 `users.id` |
| `created_at` | datetime | O | 차단한 시각 |

차단하면 모든 피드에서 상대 게시물을 제외하고 서로의 팔로우를 끊는다. 차단을 풀어도 팔로우는
되살리지 않는다.

**상대가 내 게시물을 보는 것은 막지 않는다.** 역방향까지 막으려면 모든 조회에서 "상대가 나를
차단했는지"를 확인해야 해 비용이 크다.

## 24. hashtags

해시태그다. 본문에서 뽑아 저장한다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | bigint | PK, O | 태그 ID |
| `name` | varchar | UK, O | `#`을 뺀 이름. 소문자로 정규화해 저장한다 |
| `post_count` | integer | O | 이 태그가 달린 게시물 수. 자동완성 정렬 기준 |

`#` 뒤의 한글·영문·숫자·밑줄만 태그로 인정하고 공백이나 그 밖의 문자에서 끊는다. 여러 단어를
담으려면 붙여 써야 한다. 게시물당 20개, 태그당 50자를 넘지 않는다.

`post_count`는 게시물 작성·수정·삭제에 맞춰 증감시킨다. 자주 쓰일 태그는
`HashtagSeedInitializer`가 미리 만들어 둔다. 처음 쓰는 태그를 동시에 저장하면 이름 고유 제약에
걸려 요청 하나가 실패할 수 있는데, 미리 만들어 두면 그 상황을 줄일 수 있다.

## 25. post_hashtags

게시물과 해시태그의 연결이다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `post_id` | bigint | PK 일부, FK, O | `posts.id` |
| `hashtag_id` | bigint | PK 일부, FK, O | `hashtags.id` |

게시물의 태그 조회와 태그별 게시물 조회가 모두 필요해 역방향 인덱스를 둔다.

인덱스: `idx_post_hashtags_hashtag_id`

## 26. reports

신고다. 접수만 하며 **처리 상태를 바꾸는 관리자 기능은 아직 없다.**

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | bigint | PK, O | 신고 ID |
| `reporter_id` | bigint | FK, O | 신고자 `users.id` |
| `target_type` | varchar | O | `POST`, `COMMENT`, `USER` |
| `target_id` | bigint | O | 신고 대상 ID |
| `reason` | text | O | 신고 사유 |
| `status` | varchar | O | `PENDING`, `RESOLVED` |
| `handled_by` | bigint | FK, X | 처리한 관리자 `users.id`. 처리 전이면 NULL |
| `handled_at` | datetime | X | 처리 시각 |
| `created_at` | datetime | O | 접수시각 |

**`target_id`에는 외래키가 없다.** 대상이 게시물·댓글·사용자로 달라져 한 테이블을 가리킬 수
없기 때문이다. 대상이 실제로 있는지는 애플리케이션에서 확인한다.

같은 사용자가 같은 대상을 다시 신고하면 거절한다. `V11__add_reports_unique_constraint.sql`에서
`uk_reports_reporter_target(reporter_id, target_type, target_id)` 고유 제약을 추가했다.
코드로만 막으면 같은 요청이 동시에 들어올 때 중복 행이 남는다.
`idx_reports_status_created_at` 은 관리자 화면이 대기 중인 신고부터 보기 위한 것이다.

`status` 는 `PENDING`·`REVIEWING`·`RESOLVED`·`REJECTED` 다. `REVIEWING` 은 관리자가 여럿일 때
같은 신고를 두 사람이 동시에 들여다보는 것을 줄이기 위한 값이다. 확인만 하고 조치하지 않은 것과
아직 아무도 보지 않은 것을 구분하지 못하면 대기 목록이 같은 항목으로 계속 채워진다.

## 27. notifications

알림이다. **커뮤니티 전용이 아니다.** 알림을 만드는 쪽이 받는 사람·종류·대상을 넘기면 쌓이고,
읽는 쪽은 알림 종류를 몰라도 목록을 보여줄 수 있다. 다른 도메인에서 알림이 필요해지면 같은
방식으로 부르면 된다.

| 컬럼 | 자료형 | 키·필수 | 의미 |
| --- | --- | --- | --- |
| `id` | bigint | PK, O | 알림 ID |
| `recipient_id` | bigint | FK, O | 알림 받을 `users.id` |
| `actor_id` | bigint | FK, X | 행동한 `users.id`. 시스템 알림에는 없다 |
| `type` | varchar | O | `POST_LIKE`, `COMMENT`, `COMMENT_REPLY`, `COMMENT_LIKE`, `FOLLOW` |
| `target_type` | varchar | O | `POST`, `COMMENT`, `USER` |
| `target_id` | bigint | O | 눌렀을 때 이동할 대상 ID |
| `read_at` | datetime | X | 읽은 시각. 비어 있으면 안 읽은 알림 |
| `created_at` | datetime | O | 생성시각 |

**`type`을 문자열로 둔다.** 새 알림 종류를 추가할 때 마이그레이션이 필요 없다.

**`target_id`에는 외래키가 없다.** 종류마다 가리키는 테이블이 달라 `reports`와 같은 이유다.
대상이 지워졌을 수 있으므로 이동한 화면에서 못 찾을 수 있다.

인덱스는 둘이다. `idx_notifications_recipient_id(recipient_id, id DESC)`는 "내 알림을 최신순으로"
조회에 쓴다. `idx_notifications_unread`는 안 읽은 행만 담는 부분 인덱스로, 읽은 알림이 쌓여도
안 읽은 수 조회 비용이 늘지 않게 한다.

`V14__create_notifications_table.sql`에서 추가한다.

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
| `users` 1 : N `posts` | 사용자는 여러 게시물을 쓴다 |
| `posts` 1 : N `post_media` | 게시물은 사진·영상을 여러 개 가진다 |
| `posts` 1 : N `post_place_tags` | 게시물은 장소를 여러 개 태그할 수 있다 |
| `places` 1 : N `post_place_tags` | 장소는 여러 게시물에 태그된다 |
| `posts` 1 : N `comments` | 게시물은 여러 댓글을 가진다 |
| `comments` 1 : N `comments` | 댓글은 답글을 여러 개 가진다. 답글에는 답글이 없다 |
| `users` N : M `posts` (`post_likes`) | 좋아요. 같은 조합은 한 번만 |
| `users` N : M `comments` (`comment_likes`) | 댓글 좋아요. 같은 조합은 한 번만 |
| `users` N : M `posts` (`bookmarks`) | 저장. 같은 조합은 한 번만 |
| `users` N : M `users` (`follows`) | 팔로우. 방향이 있다 |
| `users` N : M `users` (`blocks`) | 차단. 방향이 있다 |
| `posts` N : M `hashtags` (`post_hashtags`) | 게시물의 해시태그 |
| `users` 1 : N `reports` | 사용자는 여러 건을 신고할 수 있다. 신고 대상은 FK로 연결하지 않는다 |
| `users` 1 : N `notifications` | 사용자는 여러 알림을 받는다. `recipient_id`와 `actor_id`로 두 번 연결된다. 알림 대상은 FK로 연결하지 않는다 |

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
