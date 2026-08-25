# 부산 여행 일정 서비스 API 명세

> 구현 상태 안내
>
> - 일정 생성 V2와 기존 V1 생성 API를 모두 제공한다.
> - `POST /schedules`에 `Idempotency-Key`가 있으면 V2 Preview 요청, 없으면 기존 V1 요청으로 처리한다.
> - 신규 프론트는 V2 계약을 사용하며 V1은 기존 클라이언트 호환용이다.

## 공통

| 구분 | 내용 |
| --- | --- |
| Base URL | `/api/v1` |
| Content Type | `application/json; charset=utf-8` |
| 인증 | 아직 없음. 커뮤니티 API는 `X-User-Id` 헤더로 요청자를 임시 식별한다 |
| 좌표 | WGS84, `longitude`는 경도, `latitude`는 위도 |
| 향후 계획 | 사용자 도메인 도입 후 JWT와 사용자별 일정 조회 적용 |

> **`X-User-Id`는 임시 조치다.** 헤더 값을 바꾸면 다른 사용자를 사칭할 수 있으므로 인증 도입 시
> 반드시 제거해야 한다. 요청 본문이 아니라 헤더로 받는 이유는, 조회 API에는 본문이 없어 방식이
> 갈라지고, 나중에 `Authorization` 헤더로 교체할 때 요청 DTO를 건드리지 않아도 되기 때문이다.

## 엔드포인트

| 기능 | Method | URI | 성공 상태 |
| --- | --- | --- | --- |
| 사전 질문 조회 | GET | `/trip-questions` | `200 OK` |
| 출발지·도착지 검색 | GET | `/locations/search` | `200 OK` |
| 일정 Planner 생성 | POST | `/schedules` | `201 Created` |
| 일정 목록 조회 | GET | `/schedules` | `200 OK` |
| 일정 수정 | PATCH | `/schedules/{scheduleId}` | `200 OK` |
| 장소 검색 | GET | `/places` | `200 OK` |
| 장소 상세 | GET | `/places/{placeId}` | `200 OK` |
| 주변 편의시설 | GET | `/places/{placeId}/nearby-facilities` | `200 OK` |
| 일정 지도 | GET | `/schedules/{scheduleId}/map` | `200 OK` |
| 공유 링크 생성 | POST | `/schedules/{scheduleId}/shares` | `201 Created` |
| 공유 일정 조회 | GET | `/shared-schedules/{token}` | `200 OK` |
| 공유 일정 지도 | GET | `/shared-schedules/{token}/map` | `200 OK` |
| 공유 링크 폐기 | DELETE | `/schedules/{scheduleId}/shares/{shareId}` | `204 No Content` |

### 일정 생성 V2 엔드포인트

| 기능 | Method | URI | 성공 상태 | 구현 상태 |
| --- | --- | --- | --- | --- |
| 일정 조건 Preview 생성 | POST | `/schedule-previews` | `201 Created` | 구현 |
| 일정 조건 Preview 조회 | GET | `/schedule-previews/{previewId}` | `200 OK` | 구현 |
| 내부·Kakao 통합 장소 검색 | GET | `/places?scope=ALL` | `200 OK` | 구현 |
| 외부 장소 내부 ID 확정 | POST | `/places/resolve` | `200 OK` | 구현 |
| Preview 기반 일정 생성 | POST | `/schedules` | `201 Created` | 구현 |
| 일정 단건 조회 | GET | `/schedules/{scheduleId}` | `200 OK` | 구현 |

### 커뮤니티 엔드포인트

계약 상세는 `커뮤니티 계약`을 본다. `요청자` 열의 `필수`는 `X-User-Id` 헤더가 없으면 요청이
실패한다는 뜻이고, `선택`은 없어도 동작하되 좋아요·저장 여부가 `false`로 나간다는 뜻이다.

| 기능 | Method | URI | 성공 상태 | 요청자 |
| --- | --- | --- | --- | --- |
| 게시물 작성 | POST | `/posts` | `201 Created` | 필수 |
| 피드 조회 | GET | `/posts` | `200 OK` | 선택 |
| 인기 피드 | GET | `/posts/popular` | `200 OK` | 선택 |
| 게시물 상세 | GET | `/posts/{postId}` | `200 OK` | 선택 |
| 게시물 수정 | PATCH | `/posts/{postId}` | `200 OK` | 필수 |
| 게시물 삭제 | DELETE | `/posts/{postId}` | `204 No Content` | 필수 |
| 내가 지운 게시물 | GET | `/posts/me/deleted` | `200 OK` | 필수 |
| 게시물 복구 | POST | `/posts/{postId}/restore` | `200 OK` | 필수 |
| 좋아요 | POST | `/posts/{postId}/likes` | `200 OK` | 필수 |
| 좋아요 취소 | DELETE | `/posts/{postId}/likes` | `200 OK` | 필수 |
| 저장 | POST | `/posts/{postId}/bookmarks` | `200 OK` | 필수 |
| 저장 해제 | DELETE | `/posts/{postId}/bookmarks` | `200 OK` | 필수 |
| 내 저장 목록 | GET | `/users/me/bookmarks` | `200 OK` | 필수 |
| 댓글 작성 | POST | `/posts/{postId}/comments` | `201 Created` | 필수 |
| 댓글 목록 | GET | `/posts/{postId}/comments` | `200 OK` | 선택 |
| 댓글 수정 | PATCH | `/posts/{postId}/comments/{commentId}` | `200 OK` | 필수 |
| 댓글 삭제 | DELETE | `/posts/{postId}/comments/{commentId}` | `204 No Content` | 필수 |
| 댓글 좋아요 | POST | `/posts/{postId}/comments/{commentId}/likes` | `200 OK` | 필수 |
| 댓글 좋아요 취소 | DELETE | `/posts/{postId}/comments/{commentId}/likes` | `200 OK` | 필수 |
| 팔로우 | POST | `/users/{userId}/follows` | `200 OK` | 필수 |
| 팔로우 취소 | DELETE | `/users/{userId}/follows` | `200 OK` | 필수 |
| 팔로워 목록 | GET | `/users/{userId}/followers` | `200 OK` | 불필요 |
| 팔로잉 목록 | GET | `/users/{userId}/followings` | `200 OK` | 불필요 |
| 차단 | POST | `/users/{userId}/blocks` | `200 OK` | 필수 |
| 차단 해제 | DELETE | `/users/{userId}/blocks` | `200 OK` | 필수 |
| 내 차단 목록 | GET | `/users/me/blocks` | `200 OK` | 필수 |
| 프로필 조회 | GET | `/users/{userId}/profile` | `200 OK` | 선택 |
| 사용자 게시물 | GET | `/users/{userId}/posts` | `200 OK` | 선택 |
| 닉네임 변경 | PATCH | `/users/me/nickname` | `200 OK` | 필수 |
| 프로필 사진 변경 | PATCH | `/users/me/profile-image` | `200 OK` | 필수 |
| 프로필 사진 제거 | DELETE | `/users/me/profile-image` | `200 OK` | 필수 |
| 사용자 검색 | GET | `/users/search` | `200 OK` | 불필요 |
| 해시태그 자동완성 | GET | `/hashtags/search` | `200 OK` | 불필요 |
| 내 알림 목록 | GET | `/notifications` | `200 OK` | 필수 |
| 안 읽은 알림 수 | GET | `/notifications/unread-count` | `200 OK` | 필수 |
| 알림 읽음 | PATCH | `/notifications/{notificationId}/read` | `200 OK` | 필수 |
| 알림 모두 읽음 | PATCH | `/notifications/read-all` | `200 OK` | 필수 |
| 신고 | POST | `/reports` | `201 Created` | 필수 |

## 1. 사전 질문 조회

`GET /api/v1/trip-questions`

요청 파라미터와 요청 본문이 없다.

```json
{
  "items": [
    {
      "id": "COMPANION",
      "text": "누구와 여행하나요?",
      "type": "SINGLE_CHOICE",
      "required": true,
      "minSelections": 1,
      "maxSelections": 1,
      "displayOrder": 1,
      "answers": [
        {
          "id": "COMPANION_PARENTS",
          "label": "부모님과",
          "displayOrder": 1
        }
      ]
    }
  ]
}
```

## 2. 출발지·도착지 검색

`GET /api/v1/locations/search?keyword={keyword}&size={size}`

| Query | 필수 | 설명 |
| --- | :---: | --- |
| `keyword` | O | 검색할 장소명 |
| `size` | X | 최대 결과 수. 생략 시 `10` |

```json
{
  "items": [
    {
      "name": "부산역",
      "address": "부산 동구 중앙대로 206",
      "longitude": 129.0403,
      "latitude": 35.1151,
      "externalId": "kakao-place-id",
      "source": "KAKAO_LOCAL"
    }
  ]
}
```

Kakao Local 키워드 검색을 사용한다. 검색 결과 전체는 저장하지 않고 사용자가 선택한 이름과 좌표만 일정에 저장한다.

## 일정 생성 V2 계약

상세 제품 동작은 `docs/schedule-generation-v2-spec.md`, 프론트 연동 방식은 `docs/frontend-schedule-v2-handoff.md`를 따른다.

### V2-1. 질문 조회 확장

`GET /api/v1/trip-questions`

V2에서는 질문 응답에 선택 수를 추가한다.

```json
{
  "items": [
    {
      "id": "THEME",
      "text": "어떤 여행을 선호하나요?",
      "type": "MULTIPLE_CHOICE",
      "required": true,
      "minSelections": 1,
      "maxSelections": 3,
      "uiStep": 3,
      "displayOrder": 5,
      "answers": [
        {
          "id": "THEME_FOOD",
          "label": "맛집",
          "displayOrder": 1
        }
      ]
    }
  ]
}
```

- `SINGLE_CHOICE`는 `maxSelections=1`이며 필수 질문은 `minSelections=1`, 선택 질문은 `minSelections=0`이다.
- `MULTIPLE_CHOICE`는 질문별 선택 수 범위를 응답한다.
- 프론트는 배열 위치가 아니라 `uiStep`으로 Step1~3 질문을 그룹화한다.
- 질문·답변 ID는 선택 저장과 화면별 디자인 분기에 사용하는 안정적인 계약이다. 문구와 선택 수는 API 응답을 사용한다.

| `uiStep` | 질문 ID | 활성 답변 ID |
| ---: | --- | --- |
| 1 | `COMPANION` | `COMPANION_SOLO`, `COMPANION_FRIENDS`, `COMPANION_COUPLE`, `COMPANION_FAMILY_WITH_CHILD`, `COMPANION_PARENTS`, `COMPANION_OTHER` |
| 1 | `MOBILITY` | `MOBILITY_NORMAL`, `MOBILITY_LOW_WALK` |
| 2 | `PACE` | `PACE_PACKED`, `PACE_RELAXED` |
| 2 | `TRANSIT` | `TRANSIT_SIMPLE`, `TRANSIT_FAST` |
| 3 | `THEME` | `THEME_FOOD`, `THEME_NATURE`, `THEME_HISTORY_CULTURE`, `THEME_SEA`, `THEME_SHOPPING`, `THEME_HEALING` |

### V2-2. 통합 장소 검색

`GET /api/v1/places?keyword={keyword}&scope={scope}&size={size}`

| Query | 필수 | 설명 |
| --- | :---: | --- |
| `keyword` | O | 장소명 검색어 |
| `scope` | X | `INTERNAL`, `ALL`. 기본 `INTERNAL` |
| `size` | X | 최대 결과 수. 기본 `20`, 최대 `50` |

`scope=ALL`이면 내부 장소를 우선 반환하고 결과가 부족할 때 Kakao Local 후보를 보강한다.

```json
{
  "items": [
    {
      "placeId": 101,
      "source": "TOUR_API",
      "externalId": "tour-content-id",
      "name": "광안리해수욕장",
      "category": "관광지",
      "categoryLabel": "관광지",
      "address": "부산광역시 수영구",
      "longitude": 129.1186,
      "latitude": 35.1532,
      "primaryImageUrl": "https://...",
      "resolved": true
    },
    {
      "placeId": null,
      "source": "KAKAO_LOCAL",
      "externalId": "kakao-place-id",
      "name": "사용자 검색 장소",
      "category": "카페",
      "categoryLabel": "카페",
      "address": "부산광역시 ...",
      "longitude": 129.12,
      "latitude": 35.15,
      "primaryImageUrl": null,
      "resolved": false
    }
  ]
}
```

검색만으로 외부 장소를 DB에 저장하지 않는다.

호환 기간에는 내부 장소 항목에 기존 필드 `id`, `externalContentId`도 각각 `placeId`, `externalId`와 같은 값으로 함께 반환한다. 신규 프론트는 V2 필드명을 사용한다.

### V2-3. 외부 장소 Resolve

`POST /api/v1/places/resolve`

```json
{
  "source": "KAKAO_LOCAL",
  "externalId": "kakao-place-id",
  "name": "사용자 검색 장소",
  "category": "카페",
  "address": "부산광역시 ...",
  "longitude": 129.12,
  "latitude": 35.15,
  "placeUrl": "https://place.map.kakao.com/..."
}
```

허용 `source`는 `KAKAO_LOCAL`, `NAVER_LOCAL`이다. 그 외 값은 `400 INVALID_EXTERNAL_PLACE`다.

네이버 지역검색은 안정적인 장소 ID를 제공하지 않으므로, `NAVER_LOCAL`의 `externalId`는 원본 응답의 `mapx`와 `mapy`를 `-`로 이어 붙인 값을 사용한다(예: `1291598546-351585232`). `longitude`, `latitude`에는 `mapx`, `mapy`를 `10000000`으로 나눈 WGS84 좌표를 전달한다.

서버는 source, 좌표 범위, 필수 필드를 검증하고 `(source, external_content_id)` 기준으로 upsert한다. 신규·기존 여부와 관계없이 `200 OK`로 내부 장소를 반환한다.

같은 실물 장소가 중복 적재되지 않도록, 아직 등록되지 않은 외부 장소는 먼저 기존 장소와 대조한다. 좌표가 100m 이내이고 공백·기호를 제거한 이름이 일치하거나 서로 포함하면 **그 장소의 `placeId`를 그대로 반환한다.** 이 경우 응답의 `source`는 요청한 값이 아니라 기존 장소의 출처(예: `TOUR_API`)이며, 서버가 적재해 둔 이름·주소·운영정보·이미지는 요청 값으로 덮어쓰지 않는다.

`NAVER_LOCAL`로 새 장소를 만들 때는 카테고리 문자열에서 TourAPI 콘텐츠 유형을 추정해 저장한다(음식·카페 → 음식점, 축제·공연 → 축제공연행사, 박물관·미술관 → 문화시설, 쇼핑·시장 → 쇼핑, 숙박 → 숙박, 레포츠·체험 → 레포츠, 그 외 → 관광지). 체류시간과 테마 반영이 내부 적재 장소와 동일하게 동작하도록 하기 위함이다.

외부 검색으로 등록한 장소는 운영시간 정보가 없다. 일정 생성 시 해당 방문지의 `warnings`에 `"운영시간 정보가 없어 방문 전 확인이 필요합니다."`가 포함되며, 운영시간을 이유로 일정 생성을 거부하지는 않는다.

```json
{
  "placeId": 901,
  "source": "KAKAO_LOCAL",
  "externalId": "kakao-place-id",
  "name": "사용자 검색 장소",
  "category": "카페",
  "categoryLabel": "카페",
  "address": "부산광역시 ...",
  "longitude": 129.12,
  "latitude": 35.15,
  "primaryImageUrl": null,
  "placeUrl": "https://place.map.kakao.com/...",
  "resolved": true,
  "operatingInfoAvailable": false
}
```

Preview의 `mustVisitPlaceIds`와 `fixedEvents[].placeId`에는 Resolve된 내부 ID만 전달한다.

### V2-4. Preview 생성

`POST /api/v1/schedule-previews`

기본 생성 화면의 필수 여행 입력은 `startDate`, `endDate`, `startLocation`, `lodgingPlan`이다. 질문 단계가 끝난 뒤에는 활성 필수 질문을 모두 포함한 `selectedAnswers`도 필수다. 숙소를 아직 정하지 않았다면 `lodgingPlan`에 반드시 `{"mode":"UNDECIDED"}`를 전달한다. 현재 기본 화면은 `startTime`, `endConstraint`, `customPrompt`를 `null` 또는 생략하고 `fixedEvents=[]`, `dayOverrides=[]`를 전달한다. 숙소·종료 제약·행사·일차별 조정·자유 요청은 API는 지원하지만 프론트 1차 범위에서는 `Deferred`다.

```json
{
  "startDate": "2026-07-16",
  "endDate": "2026-07-18",
  "startLocation": {
    "name": "부산역",
    "longitude": 129.0403,
    "latitude": 35.1151
  },
  "startTime": "14:00",
  "lodgingPlan": {
    "mode": "UNDECIDED"
  },
  "endConstraint": {
    "type": "FLIGHT_DEPARTURE",
    "location": {
      "name": "김해국제공항",
      "longitude": 128.9485,
      "latitude": 35.1732
    },
    "targetAt": "2026-07-18T18:00:00+09:00",
    "bufferMinutes": 90
  },
  "selectedAnswers": [
    {
      "questionId": "COMPANION",
      "answerIds": ["COMPANION_FRIENDS"]
    },
    {
      "questionId": "THEME",
      "answerIds": ["THEME_FOOD", "THEME_NATURE"]
    },
    {
      "questionId": "PACE",
      "answerIds": ["PACE_RELAXED"]
    },
    {
      "questionId": "MOBILITY",
      "answerIds": ["MOBILITY_LOW_WALK"]
    },
    {
      "questionId": "TRANSIT",
      "answerIds": ["TRANSIT_SIMPLE"]
    }
  ],
  "mustVisitPlaceIds": [101, 205],
  "fixedEvents": [
    {
      "clientEventId": "event-1",
      "name": "공연",
      "placeId": 901,
      "startsAt": "2026-07-17T19:00:00+09:00",
      "endsAt": "2026-07-17T21:00:00+09:00"
    }
  ],
  "dayOverrides": [
    {
      "date": "2026-07-17",
      "availableFrom": "11:00",
      "availableUntil": "22:00"
    }
  ],
  "customPrompt": "바다를 많이 보고 걷는 구간은 적었으면 좋겠어요"
}
```

#### 숙소 모드

`UNDECIDED`:

```json
{
  "mode": "UNDECIDED"
}
```

`FIXED_BASE`:

```json
{
  "mode": "FIXED_BASE",
  "baseLocation": {
    "name": "해운대 숙소",
    "longitude": 129.158,
    "latitude": 35.159
  }
}
```

`PER_NIGHT`:

```json
{
  "mode": "PER_NIGHT",
  "nightStays": [
    {
      "date": "2026-07-16",
      "location": {
        "name": "해운대 숙소",
        "longitude": 129.158,
        "latitude": 35.159
      }
    },
    {
      "date": "2026-07-17",
      "location": {
        "name": "남포동 숙소",
        "longitude": 129.032,
        "latitude": 35.1
      }
    }
  ]
}
```

#### Preview 성공 응답

```json
{
  "previewId": "preview-uuid",
  "status": "READY",
  "canGenerate": true,
  "expiresAt": "2026-07-15T15:30:00+09:00",
  "timeZone": "Asia/Seoul",
  "lodgingMode": "UNDECIDED",
  "routeCoverage": "ATTRACTION_ROUTES_ONLY",
  "resolvedDays": [
    {
      "date": "2026-07-16",
      "availableFrom": "14:00",
      "availableUntil": "20:00",
      "startLocation": {
        "name": "부산역",
        "longitude": 129.0403,
        "latitude": 35.1151
      },
      "endLocation": null,
      "startLocationSource": "USER",
      "endLocationSource": "PLANNER_DECIDES"
    },
    {
      "date": "2026-07-17",
      "availableFrom": "11:00",
      "availableUntil": "22:00",
      "startLocation": null,
      "endLocation": null,
      "startLocationSource": "PLANNER_DECIDES",
      "endLocationSource": "PLANNER_DECIDES"
    },
    {
      "date": "2026-07-18",
      "availableFrom": "10:00",
      "availableUntil": "16:30",
      "startLocation": null,
      "endLocation": {
        "name": "김해국제공항",
        "longitude": 128.9485,
        "latitude": 35.1732
      },
      "startLocationSource": "PLANNER_DECIDES",
      "endLocationSource": "END_CONSTRAINT"
    }
  ],
  "resolvedEndConstraint": {
    "type": "FLIGHT_DEPARTURE",
    "targetAt": "2026-07-18T18:00:00+09:00",
    "appliedBufferMinutes": 90,
    "availableUntil": "16:30"
  },
  "appliedDefaults": [
    {
      "fieldPath": "resolvedDays[1].availableFrom",
      "resolvedValue": "10:00",
      "reasonCode": "DEFAULT_FULL_DAY_START"
    }
  ],
  "interpretedPrompt": {
    "preferences": ["LOW_WALKING", "PREFER_SEA_VIEW"],
    "unrecognizedTexts": [],
    "source": "HYBRID_AI",
    "confidence": 92
  },
  "warnings": [
    {
      "code": "LODGING_ROUTE_EXCLUDED",
      "date": null,
      "message": "숙소 이동시간은 일정 경로에 포함되지 않습니다."
    }
  ],
  "conflicts": []
}
```

- Preview 유효시간은 30분이다.
- `status`는 `READY`, `REQUIRES_ACTION`, `EXPIRED`, `CONSUMED`다.
- `canGenerate=false`이면 `conflicts`가 비어 있지 않아야 한다.
- `UNDECIDED`에서 위치가 정해지지 않은 일차는 `null`과 `PLANNER_DECIDES`로 반환한다.
- Preview 단계에서는 ODsay·TMAP 상세 경로를 호출하지 않는다.
- `interpretedPrompt.source`는 `RULE_BASED`, `HYBRID_AI`, `FALLBACK` 중 하나다. AI Planner가 비활성화되면 `RULE_BASED`, AI 해석에 성공하면 `HYBRID_AI`, 활성화됐지만 키 누락·시간 초과·Provider 오류·출력 검증 실패가 발생하면 `FALLBACK`이다.
- AI 해석은 `customPrompt`의 소프트 선호만 다룬다. 필수 장소, 고정 행사, 종료 제약과 일차별 시간은 AI 출력으로 추가·삭제·변경하지 않는다.
- 마지막 날에 `endConstraint`가 있으면 같은 날짜의 `dayOverrides.endLocation`은 함께 전달할 수 없다.

### V2-5. Preview 조회

`GET /api/v1/schedule-previews/{previewId}`

- 유효한 Preview는 생성 응답과 같은 구조를 반환한다.
- 소비된 Preview는 `status=CONSUMED`, `scheduleId`를 반환한다.
- 만료된 Preview는 `410 PREVIEW_EXPIRED`를 반환한다.
- 존재하지 않는 Preview는 `404 SCHEDULE_PREVIEW_NOT_FOUND`를 반환한다.

### V2-6. Preview 기반 일정 생성

`POST /api/v1/schedules`

```http
Idempotency-Key: 9bd292fd-5f2a-4ce4-9002-7ac511cdd4ea
Content-Type: application/json
```

```json
{
  "previewId": "preview-uuid"
}
```

- `Idempotency-Key`는 UUID 문자열을 권장하며 필수이고 최대 128자다.
- 같은 키와 같은 요청은 최초 응답을 재생하고 일정 행을 추가하지 않는다.
- 같은 키를 다른 Preview에 사용하면 `409 IDEMPOTENCY_KEY_REUSED`를 반환한다.
- 다른 키로 이미 소비된 Preview를 실행하면 `409 PREVIEW_ALREADY_CONSUMED`를 반환하고 `scheduleId`를 제공한다.
- Preview의 저장된 입력과 `resolvedDays`만 Planner에 전달한다.
- 성공 응답은 현재 `ScheduleResponse`를 기반으로 하되 `previewId`, `planningAssumptions`를 추가한다.
- 실제 경로 계산 후 선택 방문지를 줄여야 시간 조건을 만족할 수 있으면 일정을 실패시키지 않고 `planningAssumptions.warnings`에 `OPTIONAL_STOPS_REDUCED_FOR_FEASIBILITY`를 추가한다. 필수 방문지와 고정 행사는 제거하지 않는다.

다음은 V1 응답에 추가되거나 의미가 변경되는 필드만 표시한 예시다.

```json
{
  "id": "schedule-uuid",
  "previewId": "preview-uuid",
  "status": "CONFIRMED",
  "startDate": "2026-07-16",
  "endDate": "2026-07-18",
  "styleSummary": "친구와 함께하는 음식·자연 중심 일정",
  "planningAssumptions": {
    "timeZone": "Asia/Seoul",
    "lodgingMode": "UNDECIDED",
    "routeCoverage": "ATTRACTION_ROUTES_ONLY",
    "warnings": ["LODGING_ROUTE_EXCLUDED"]
  }
}
```

V2에서는 top-level `dailyStartTime`, `dailyEndTime`을 제거하고 일차별 시간만 `days[].startTime`, `days[].endTime`에 반환한다. Preview에서는 숙소 미정 일차의 출발·도착 위치가 `null`일 수 있지만, 일정 생성 완료 후 Planner가 첫 방문지와 마지막 방문지로 확정하므로 일정 상세와 지도 마커에는 값이 존재한다.

### V2-7. 일정 단건 조회와 목록 정렬

`GET /api/v1/schedules/{scheduleId}`

- 저장된 상세 일정과 `planningAssumptions`를 반환한다.
- 생성 시점에만 계산하고 저장하지 않은 `evaluation` 운영 지표는 생략한다.
- 존재하지 않는 일정은 `404 SCHEDULE_NOT_FOUND`를 반환한다.

`GET /api/v1/schedules`는 V2부터 `startDate ASC`, 같은 시작일은 `createdAt DESC` 순서로 반환한다. 인증 도입 전까지 전체 일정 반환 정책은 유지한다. 목록 응답은 축약 필드만 담으므로 상세 데이터가 필요하면 단건 조회를 사용한다. 응답 형식은 `4. 일정 목록 조회`를 참고한다.

### V2-8. V2 오류·충돌 코드

Preview의 사용자 수정 가능 충돌은 HTTP 오류가 아니라 `201 Created`, `status=REQUIRES_ACTION`, `canGenerate=false`, `conflicts[]`로 반환한다. 형식·관계가 잘못돼 Preview 자체를 만들 수 없는 요청만 `400`을 반환한다. Planner의 실제 경로 계산에서 새로 확인된 실행 불가능 조건은 `422`를 반환한다.

| 전달 위치 | HTTP | 오류·충돌 코드 | 상황 |
| --- | ---: | --- | --- |
| HTTP 오류 | 400 | `INVALID_SCHEDULE_PREVIEW_REQUEST` | Preview 필드·관계 검증 실패 |
| HTTP 오류 | 400 | `FIXED_BASE_LOCATION_REQUIRED` | `FIXED_BASE` 위치 누락 |
| HTTP 오류 | 400 | `PER_NIGHT_LOCATION_MISSING` | 숙박일 위치 누락 |
| HTTP 오류 | 400 | `MUST_VISIT_PLACE_LIMIT_EXCEEDED` | 필수 장소 상한 초과 |
| HTTP 오류 | 404 | `SCHEDULE_PREVIEW_NOT_FOUND` | 존재하지 않는 Preview 조회 또는 생성 요청 |
| HTTP 오류 | 409 | `IDEMPOTENCY_KEY_REUSED` | 같은 키를 다른 요청에 사용 |
| HTTP 오류 | 409 | `PREVIEW_ALREADY_CONSUMED` | 이미 다른 생성 요청에서 소비 |
| HTTP 오류 | 409 | `SCHEDULE_CREATION_IN_PROGRESS` | 같은 멱등성 키의 생성 요청이 아직 진행 중 |
| HTTP 오류 | 410 | `PREVIEW_EXPIRED` | Preview 만료 |
| Preview `conflicts[]` | 201 | `INSUFFICIENT_AVAILABLE_TIME` | 해당 날짜 일정 가능 시간 부족 |
| Preview `conflicts[]` | 201 | `FIXED_EVENT_CONFLICT` | 고정 행사 시간 중복 |
| Planner 오류 | 422 | `FIXED_EVENT_UNREACHABLE` | 실제 경로로 행사 시간 충족 불가 |
| Planner 오류 | 422 | `END_CONSTRAINT_UNREACHABLE` | 마지막 도착 제약 충족 불가 |

`REQUIRES_ACTION` Preview는 필요할 때 다음 상세 필드를 포함한다.

```json
{
  "previewId": "preview-uuid",
  "status": "REQUIRES_ACTION",
  "canGenerate": false,
  "conflicts": [
    {
      "code": "INSUFFICIENT_AVAILABLE_TIME",
      "message": "7월 17일에 일정을 구성할 시간이 부족합니다.",
      "fieldPath": "dayOverrides[2026-07-17].availableUntil",
      "conflictDate": "2026-07-17",
      "requiredMinutes": 180,
      "availableMinutes": 120,
      "adjustableFields": ["dayOverrides[2026-07-17].availableUntil"]
    }
  ]
}
```

## 3. 규칙 기반 다일 일정 생성 (V1 현재 구현)

`POST /api/v1/schedules`

```json
{
  "startDate": "2026-06-23",
  "endDate": "2026-06-25",
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
    {
      "questionId": "COMPANION",
      "answerId": "COMPANION_PARENTS"
    },
    {
      "questionId": "THEME",
      "answerId": "THEME_NATURE"
    },
    {
      "questionId": "PACE",
      "answerId": "PACE_PACKED"
    },
    {
      "questionId": "MOBILITY",
      "answerId": "MOBILITY_NORMAL"
    },
    {
      "questionId": "TRANSIT",
      "answerId": "TRANSIT_SIMPLE"
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
      "startLocation": {
        "name": "부산역",
        "longitude": 129.0403,
        "latitude": 35.1151
      },
      "endLocation": {
        "name": "해운대 숙소",
        "longitude": 129.158,
        "latitude": 35.159
      }
    },
    {
      "dayNo": 2,
      "startTime": "09:00",
      "endTime": "19:00",
      "startLocation": {
        "name": "해운대 숙소",
        "longitude": 129.158,
        "latitude": 35.159
      },
      "endLocation": {
        "name": "남포동 숙소",
        "longitude": 129.032,
        "latitude": 35.1
      }
    },
    {
      "dayNo": 3,
      "startTime": "09:00",
      "endTime": "17:00",
      "startLocation": {
        "name": "남포동 숙소",
        "longitude": 129.032,
        "latitude": 35.1
      },
      "endLocation": {
        "name": "김해국제공항",
        "longitude": 128.9485,
        "latitude": 35.1732
      }
    }
  ]
}
```

`mustVisitPlaceIds`는 사용자가 장소 검색에서 선택한 내부 `places.id` 목록이다. 생략할 수 있으며 중복 없이 여행 일수당 최대 5개까지 전달할 수 있다. 전달된 장소는 일정 방문지와 대중교통 경로에 반드시 포함하고 남은 슬롯은 자동 추천으로 채운다.

하루 전체 방문지는 식사·카페·고정 행사를 포함해 최대 5곳이다. 기본 속도는 가용시간 8시간 이상일 때 4곳, 6시간 이상일 때 3곳을 목표로 한다. `PACE_RELAXED`는 가용시간 6시간 이상일 때 3곳, 4시간 이상일 때 2곳을 목표로 한다. `PACE_PACKED`는 가용시간 8시간 이상일 때 5곳, 6시간 이상일 때 4곳을 목표로 한다. 3시간 미만은 여행 속도와 관계없이 1곳을 목표로 한다. 이 값은 시간 feasibility가 우선하는 Soft Target이므로 장시간 행사·필수 방문지·실제 이동시간 때문에 더 적은 결과도 정상이다.

`selectedAnswers`는 활성 필수 질문마다 정확히 하나씩 전달해야 한다. 존재하지 않는 질문·답변, 질문에 속하지 않은 답변, 같은 질문의 중복 응답은 `400 INVALID_SCHEDULE_CONDITION`을 반환한다.

여행 기간은 시작일과 종료일을 포함해 최대 4일이다. `days`는 일차별 출발지, 도착지, 시작시각, 종료시각이며 전달할 경우 여행 기간의 모든 일차를 중복 없이 포함해야 한다. 생략하면 하위 호환을 위해 모든 일차에 `dailyStartTime`, `dailyEndTime`, `startLocation`, `endLocation`을 적용한다. 실제 이동시간과 체류시간이 일차 가용시간을 넘으면 체류시간을 최소 30분까지 줄이고, 그래도 맞출 수 없으면 `400 INVALID_SCHEDULE_CONDITION`을 반환한다.

```json
{
  "id": "schedule-uuid",
  "status": "CONFIRMED",
  "startDate": "2026-06-23",
  "endDate": "2026-06-25",
  "dailyStartTime": "09:00",
  "dailyEndTime": "19:00",
  "styleSummary": "부모님과 함께하는 로컬 중심 일정",
  "days": [
    {
      "dayNo": 1,
      "date": "2026-06-23",
      "startTime": "09:00",
      "endTime": "19:00",
      "startLocation": {
        "name": "부산역",
        "longitude": 129.0403,
        "latitude": 35.1151
      },
      "endLocation": {
        "name": "해운대 숙소",
        "longitude": 129.158,
        "latitude": 35.159
      },
      "summary": "부산역 출발 → 이송도전망대 → 해운대 숙소 도착",
      "stops": [
        {
          "id": "stop-uuid",
          "order": 1,
          "arriveAt": "09:25",
          "departAt": "10:25",
          "stayMinutes": 60,
          "place": {
            "id": 101,
            "name": "이송도전망대",
            "category": "관광지",
            "address": "부산광역시 ...",
            "longitude": 129.047956,
            "latitude": 35.075519,
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
            "destinationName": "이송도전망대",
            "summary": "26",
            "departAt": "09:00",
            "arriveAt": "09:25",
            "totalMinutes": 25,
            "walkMinutes": 5,
            "waitMinutes": 0,
            "transferCount": 0,
            "fareAmount": 1550,
            "provider": "ODSAY",
            "realtimeStatus": "UNAVAILABLE",
            "fallbackUsed": false,
            "segments": [
              {
                "order": 1,
                "mode": "BUS",
                "lineName": "26",
                "startStationId": "station-id",
                "startStationName": "부산역",
                "endStationId": "station-id",
                "endStationName": "남부민2동",
                "instruction": "부산역에서 26 승차 후 남부민2동에서 하차",
                "durationMinutes": 20,
                "distanceMeters": 3200,
                "stationCount": 8,
                "waitMinutes": 0,
                "realtimeStatus": "UNAVAILABLE"
              }
            ],
            "warnings": []
          },
          "mealTimeSlot": null,
          "waitingMinutesBefore": 0,
          "selectionReasons": [
            "사용자가 반드시 방문할 장소로 선택했습니다."
          ],
          "warnings": [
            "운영시간 원문 확인이 필요한 장소입니다."
          ]
        }
      ],
      "finalTransit": {
        "routeType": "FINAL",
        "routeOrder": 2,
        "originName": "이송도전망대",
        "destinationName": "해운대 숙소",
        "summary": "26",
        "departAt": "10:25",
        "arriveAt": "11:05",
        "totalMinutes": 40,
        "walkMinutes": 5,
        "waitMinutes": 0,
        "transferCount": 0,
        "fareAmount": 1550,
        "provider": "ODSAY",
        "realtimeStatus": "UNAVAILABLE",
        "fallbackUsed": false,
        "segments": [],
        "warnings": []
      }
    }
  ],
  "evaluation": {
    "hardGate": {
      "passed": true,
      "violations": []
    },
    "qualityScore": {
      "totalScore": 95,
      "maxScore": 100,
      "evaluationCoveragePercent": 100,
      "unusedMinutes": 75,
      "longTransitWarnings": [],
      "routeConfidence": "HIGH",
      "metrics": [
        {
          "id": "TIME_FIT",
          "label": "일정 시간 적합성",
          "score": 30,
          "maxScore": 30,
          "reason": "일별 가용 시간 안에 들어옴"
        }
      ]
    },
    "operations": {
      "generationMillis": 7820,
      "planningMode": "AI_GENERATED",
      "aiPlanConfidence": 91,
      "multiDayPlanCandidateCount": 3,
      "multiDayPlanRerankedCount": 2,
      "routeEstimateResolutionCount": 18,
      "routeEstimateCacheHitCount": 6,
      "providerEstimateCallCount": 12,
      "providerEstimateFailureCount": 0,
      "routeResolutionCount": 24,
      "routeCacheHitCount": 12,
      "providerCallCount": 10,
      "providerFailureCount": 0,
      "externalHttpCallCount": 34,
      "externalHttpFailureCount": 0,
      "odsayPathSearchCount": 10,
      "odsayLoadLaneCount": 10,
      "tmapWalkingCount": 14,
      "routeCount": 4,
      "fallbackRouteCount": 0,
      "geometryFallbackLineCount": 0,
      "totalTransitMinutes": 180,
      "totalWalkMinutes": 24,
      "totalTransferCount": 1,
      "providers": ["ODSAY"]
    }
  }
}
```

일정 생성에는 TourAPI 장소 데이터, AI 장소·날짜 제안, 제약 기반 Planner, ODsay 대중교통 경로를 사용한다. `AI_PLANNER_ENABLED=true`이고 API 키가 있으면 AI가 서버가 허용한 후보 ID 안에서 일차별 장소 구성을 제안한다. AI는 장소·날짜 후보를 만들지만 필수 방문지, 고정 행사 날짜, 일차별 방문 수, 식사 슬롯을 서버가 다시 검증하며, 결정론적 Planner가 방문 순서·실제 경로·시간 feasibility·Hard Gate를 확정한다. AI 출력 검증이나 호출이 실패하면 같은 요청을 규칙 기반 Top-K 후보로 계속 생성한다. 대중교통 승하차 전후 도보 구간은 500m를 초과할 때 TMAP 보행자 경로를 우선 사용한다. 500m 이하 연결 도보나 TMAP 실패 구간은 출발·도착 좌표를 잇는 짧은 fallback 선을 사용한다. 부산 버스·도시철도 API는 보조 검증 또는 실시간 정보 제공에 사용할 수 있다.

`evaluation`은 생성 시점의 요청과 Planner 실행을 평가한 결과다. Hard Gate 위반 일정은 저장하거나 반환하지 않으며 `400 INVALID_SCHEDULE_CONDITION`으로 실패한다. `qualityScore`는 Hard Gate를 통과한 일정의 100점 품질표이며 `unusedMinutes`, 60분 초과 이동인 `longTransitWarnings`, Provider와 geometry fallback을 반영한 `routeConfidence`를 함께 반환한다. `routeConfidence`는 실제 Provider와 상세 geometry를 사용하면 `HIGH`, fallback이 있으면 `MEDIUM`, `FAKE`·`UNKNOWN` Provider가 있으면 `LOW`다. `TIME_FIT`은 시간 초과뿐 아니라 일차별 미사용 시간이 90분을 넘을 때 30분마다 감점하며 최대 감점은 10점이다. `TRANSIT_FIT`은 전체 부담 합계를 이동 구간 수로 나눈 구간당 평균 환승 부담으로 계산한다. `operations`는 생성시간, AI 사용 상태, 경로 탐색·캐시, 외부 Provider 호출과 최종 일정 경로의 운영 지표다. `planningMode`는 AI 배치안이 최종 선택되면 `AI_GENERATED`, AI 배치안을 평가했으나 실제 경로 품질로 규칙 후보를 선택하면 `AI_ASSISTED`, AI 호출·출력 검증 실패 또는 활성화 상태의 키 미설정 시 `AI_FALLBACK`, AI를 명시적으로 비활성화한 경우 `RULE_BASED`다. `aiPlanConfidence`는 유효한 AI 제안의 `0~100` 신뢰도이며 그 외에는 `null`이다. Planner는 장소 선택·날짜 배치의 상위안을 최대 3개 보존하고, 일차 출발·도착과 첫·마지막 장소 사이의 실제 ODsay 접근성으로 재평가한다. 선택된 배치안의 일차별 순열은 좌표 비용 상위 순서와 식사 위치 다양성을 보존해 실제 경량 경로로 비교한다. 경량 재평가 호출은 요청당 기본 30회이며 선택된 구간은 경량 검색 결과를 재사용해 상세 선형·실시간·도보 정보만 보강한다. `multiDayPlanCandidateCount`는 AI 제안을 포함해 평가한 다일 배치안 수, `multiDayPlanRerankedCount`는 실제 경로 평가에 성공한 배치안 수다. `routeEstimateResolutionCount`, `routeEstimateCacheHitCount`, `providerEstimateCallCount`, `providerEstimateFailureCount`는 전체 재평가 단계를, `providerCallCount`, `routeResolutionCount`, `routeCacheHitCount`는 최종 경로 확정 단계를 집계한다. `externalHttpCallCount`는 ODsay 경로검색·`loadLane`·TMAP 도보 요청을 합친 실제 HTTP 시도 수이며 프로세스 TTL 캐시 적중 시 증가하지 않는다. `geometryFallbackLineCount`는 상세 선형을 얻지 못했거나 500m 이하 연결 도보라 단순 좌표를 사용한 지도 경로선 수다.

자동 추천은 목표 방문지 수보다 최대 6곳 큰 후보 풀을 만든다. 최대 20개 후보 범위에서 필수 장소를 모두 포함하는 장소 선택과 날짜 배치를 함께 비교한다. 전체 목표가 12곳 이하면 비트마스크 동적계획법을 사용하고 12곳을 넘으면 일차별 상위 조합과 후보·필수 마스크 다양성을 보존하는 beam DP를 사용한다. 조합 비용은 추정 이동거리, 일차별 식사 시간창의 부족·초과, 질문 답변과 정규화된 프롬프트 선호를 포함한다. 동일 장소 집합의 날짜 배정이 다른 상태도 설정된 Top-K 범위에서 보존한다. 다일 배치안과 선택된 일차별 방문 순서는 Provider-free 좌표 기반 상위 후보를 만든 뒤 요청 단위 호출 예산 안에서 실제 ODsay 경량 경로로 다시 순위를 정하고, 최종 순서에만 상세 경로를 적용한다. 고정 행사가 있으면 행사 날짜 보존을 우선하며 다일 배치안 실제 재평가는 생략한다.

Preview의 `endLocationSource=PLANNER_DECIDES`인 일차는 Planner가 방문 순서를 확정한 뒤 마지막 방문지를 종료지로 저장하고 생성 결과에 `endLocationSource=LAST_STOP`을 반환한다. 이 경우 마지막 방문 자체가 도착이므로 `finalTransit`은 `null`이다. 사용자 종료 제약, 숙소, 일차별 override가 있으면 해당 종료지와 `finalTransit`을 유지한다. 숙소 미정 중간 일차의 시작 위치도 첫 방문지로 확정하며 `startLocationSource=PLANNER_DECIDES`를 유지한다.

자동 추천은 일차 가용시간이 식사 창과 45분 이상 겹칠 때 `11:00~14:00`을 점심, `17:00~19:00`을 저녁 창으로 사용한다. 식사는 전체 일차 목표 안에서 3~4곳 일정에는 최대 1곳, 5곳 일정에는 점심·저녁 최대 2곳을 확보한다. 첫 번째와 두 번째 식사 장소의 `arriveAt`을 각각 점심과 저녁 창 안으로 정렬한다. 이 과정에서 생긴 대기시간은 종료시각 feasibility에는 포함하지만 활동시간으로 간주하지 않으므로 `unusedMinutes`와 `TIME_FIT`의 미사용 시간에 남는다. 필수 방문 장소와 고정 행사가 우선이며 음식 후보가 부족하면 가능한 수만 반영하고 일정 생성을 실패시키지 않는다.

## 4. 일정 목록 조회

`GET /api/v1/schedules`

요청 파라미터와 요청 본문이 없다. 1차 스프린트에서는 저장된 전체 일정을 반환한다.

목록은 **축약 응답만 반환한다.** 방문지(`days`), 경로(`transit`), 평가 리포트(`evaluation`)는 담지 않는다.
목록 카드 한 장을 그리는 데 필요한 정보만 내려주고, 상세가 필요하면 `GET /api/v1/schedules/{scheduleId}`를 호출한다.

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | UUID | 일정 ID |
| `status` | string | 일정 상태 |
| `startDate` / `endDate` | date | 여행 시작일과 종료일 |
| `styleSummary` | string | 일정 한 줄 요약 |
| `dayCount` | int | 총 일차 수 |
| `stopCount` | int | 전체 방문지 수 |
| `previewPlaceNames` | string[] | 카드 미리보기용 장소 이름. 방문 순서대로 최대 3개, 중복 제거 |

```json
{
  "items": [
    {
      "id": "schedule-uuid",
      "status": "CONFIRMED",
      "startDate": "2026-06-23",
      "endDate": "2026-06-25",
      "styleSummary": "부모님과 함께하는 로컬 중심 일정",
      "dayCount": 3,
      "stopCount": 12,
      "previewPlaceNames": ["이송도전망대", "감천문화마을", "자갈치시장"]
    }
  ]
}
```

향후 JWT 도입 시 URI는 유지하고 인증 사용자 ID에 해당하는 일정만 반환한다.

## 5. 일정 수정 및 경로 재계산

`PATCH /api/v1/schedules/{scheduleId}`

```json
{
  "stops": [
    {
      "stopId": "stop-a",
      "dayNo": 1,
      "order": 1,
      "stayMinutes": 70
    },
    {
      "placeId": 205,
      "dayNo": 1,
      "order": 2,
      "stayMinutes": 90
    }
  ]
}
```

- 기존 방문 계획은 `stopId`를 전달한다.
- 새 장소는 `placeId`를 전달한다.
- 한 항목에 `stopId`와 `placeId`를 동시에 전달하지 않는다.
- `stops`는 수정 후 일정에 남길 전체 방문 계획이다.
- `dayNo`는 방문 계획을 배치할 여행 일차다.
- 모든 여행 일차에 방문 장소가 한 개 이상 있어야 한다.
- 하루 방문 장소는 식사 슬롯을 포함해 최대 5개다.
- 일차별 `order`는 `1`부터 중복과 누락 없이 연속되어야 한다.
- `stayMinutes`는 최소 `30`분이다.
- 편집 토큰과 `version`을 사용하지 않는다.

요청한 체류시간의 합이 하루 가용 시간을 넘으면 요청을 거부하지 않고, 마지막 방문지부터 순서대로 최소
`30`분까지 줄여서 하루에 맞춘다. 줄어든 방문지에는 `stops[].warnings`에 다음 형태의 문구가 추가되므로
화면에서 사용자에게 알려야 한다. 최소 체류시간까지 줄여도 맞지 않으면 `400 INVALID_SCHEDULE_CONDITION`이다.

```text
하루 가용 시간에 맞춰 체류시간을 400분에서 76분으로 줄였습니다.
```

```json
{
  "id": "schedule-uuid",
  "status": "CONFIRMED",
  "days": [
    {
      "dayNo": 1,
      "stops": [
        {
          "id": "stop-a",
          "order": 1,
          "stayMinutes": 70
        },
        {
          "id": "stop-new",
          "order": 2,
          "stayMinutes": 90
        }
      ]
    }
  ]
}
```

추가·삭제·순서·일차·체류시간 변경을 반영하고 전체 일차의 대중교통 경로를 다시 계산한다. 기존 `stopId` 항목은 ID를 유지하고 `placeId` 항목은 새 방문 계획 ID를 생성한다. 실제 이동시간과 체류시간이 일차 가용시간에 맞지 않으면 체류시간을 최소 30분까지 줄이며, 그래도 초과하면 `400 INVALID_SCHEDULE_CONDITION`을 반환한다.

## 6. 장소 검색·교체 후보 조회

`GET /api/v1/places`

키워드 검색:

```http
GET /api/v1/places?keyword=전망대
```

위치 기반 검색:

```http
GET /api/v1/places?longitude=129.0403&latitude=35.1151&radius=1000
```

키워드 검색은 `keyword`를 사용한다. 위치 기반 검색은 `longitude`, `latitude`, `radius`를 사용한다. 두 방식 모두 내부 DB의 `places`를 조회한다.

```json
{
  "items": [
    {
      "id": 101,
      "externalContentId": "126508",
            "name": "이송도전망대",
            "category": "관광지",
            "categoryLabel": "관광지",
      "address": "부산 서구 암남동",
      "longitude": 129.047956,
      "latitude": 35.075519,
      "distanceMeters": 850,
      "primaryImageUrl": "https://example.com/image.jpg"
    }
  ]
}
```

`distanceMeters`는 위치 기반 검색에서만 계산하며 DB에 저장하지 않는다.

## 7. 장소 상세 조회

`GET /api/v1/places/{placeId}`

```json
{
  "id": 101,
  "source": "TOUR_API",
  "externalContentId": "126508",
  "contentTypeId": "12",
  "name": "이송도전망대",
  "category": "A01011200",
  "categoryLabel": "자연 관광지",
  "address": "부산 서구 암남동",
  "longitude": 129.047956,
  "latitude": 35.075519,
  "placeUrl": null,
  "primaryImageUrl": "https://example.com/image.jpg",
  "overview": "장소 설명",
  "operatingInfo": {
    "openingHoursText": "09:00~18:00",
    "closedDaysText": "연중무휴",
    "useFeeText": "무료",
    "parkingText": "주차 가능",
    "requiresManualCheck": true
  },
  "images": [
    {
      "url": "https://example.com/image.jpg",
      "thumbnailUrl": "https://example.com/thumbnail.jpg",
      "copyrightType": "Type1"
    }
  ]
}
```

TourAPI 기본·상세·소개·이미지 응답을 내부 DB에 적재한 결과를 조회한다.

사용자가 네이버·카카오 검색으로 직접 등록한 장소는 `overview`·`operatingInfo`·`images`가 비어 있다.
TourAPI가 그 장소를 모르고 외부 지역검색 API도 이 값들을 제공하지 않기 때문이며, **조회 실패가 아니라
정상 응답이다.** 이 경우 `placeUrl`로 외부 지도 서비스의 장소 페이지를 연결한다.

`placeUrl`이 없는 장소(TourAPI 적재분)는 `name`과 좌표로 지도 API를 조회해 같은 화면을 구성할 수 있다.

## 8. 주변 편의시설 조회

`GET /api/v1/places/{placeId}/nearby-facilities?types=CONVENIENCE_STORE&radius=1000`

`placeId`의 좌표를 기준으로 주변 편의시설을 검색한다. 1차 스프린트에서 지원하는 유형은 `CONVENIENCE_STORE`이며 Kakao Local 카테고리 `CS2`를 사용한다.

```json
{
  "items": [
    {
      "externalId": "kakao-place-id",
      "type": "CONVENIENCE_STORE",
      "name": "CU 부산역점",
      "address": "부산 동구 중앙대로",
      "longitude": 129.041,
      "latitude": 35.115,
      "distanceMeters": 120,
      "placeUrl": "https://place.map.kakao.com/...",
      "source": "KAKAO_LOCAL"
    }
  ]
}
```

검색 결과는 실시간 데이터이므로 DB에 저장하지 않는다. `ATM`, `RESTROOM` 등 지원하지 않는 유형은 `501 FACILITY_TYPE_NOT_SUPPORTED`를 반환한다.

## 9. 일정 지도 데이터 조회

`GET /api/v1/schedules/{scheduleId}/map?dayNo={dayNo}`

`dayNo`는 선택값이며 생략하면 전체 일차 데이터를 반환한다.

```json
{
  "startMarker": {
    "name": "부산역",
    "longitude": 129.0403,
    "latitude": 35.1151
  },
  "endMarker": {
    "name": "부산역",
    "longitude": 129.0403,
    "latitude": 35.1151
  },
  "markers": [
    {
      "dayNo": 1,
      "order": 1,
      "placeId": 101,
      "name": "이송도전망대",
      "arriveAt": "09:25",
      "departAt": "10:25",
      "subtitle": "관광지 · 체류 60분",
      "riskLevel": "NOTICE",
      "longitude": 129.047956,
      "latitude": 35.075519
    }
  ],
  "routeLines": [
    {
      "dayNo": 1,
      "routeOrder": 1,
      "lineOrder": 1,
      "mode": "WALK",
      "lineName": null,
      "startName": "부산역",
      "endName": "부산역",
      "durationMinutes": 5,
      "distanceMeters": 300,
      "instruction": "부산역에서 승차 정류장까지 도보 이동",
      "fallbackUsed": false,
      "coordinates": [
        [129.0403, 35.1151],
        [129.0412, 35.1146]
      ]
    },
    {
      "dayNo": 1,
      "routeOrder": 1,
      "lineOrder": 2,
      "mode": "BUS",
      "lineName": "26",
      "startName": "부산역",
      "endName": "남부민2동",
      "durationMinutes": 20,
      "distanceMeters": 3200,
      "instruction": "부산역에서 26 승차 후 남부민2동에서 하차",
      "fallbackUsed": false,
      "coordinates": [
        [129.0412, 35.1146],
        [129.0470, 35.0780]
      ]
    },
    {
      "dayNo": 1,
      "routeOrder": 1,
      "lineOrder": 3,
      "mode": "WALK",
      "lineName": null,
      "startName": "남부민2동",
      "endName": "이송도전망대",
      "durationMinutes": 4,
      "distanceMeters": 250,
      "instruction": "남부민2동에서 이송도전망대까지 도보 이동",
      "fallbackUsed": false,
      "coordinates": [
        [129.0470, 35.0780],
        [129.047956, 35.075519]
      ]
    }
  ]
}
```

서버는 저장된 경로 좌표를 반환하고 프론트엔드는 지도 SDK를 사용해 마커와 선을 그린다. 좌표 계약은 제공자와 무관하게 WGS84 `[경도, 위도]`다. `routeLines[].startName`과 `routeLines[].endName`은 해당 선 조각의 출발·도착 지점명이다. 대중교통 구간은 승하차 정류장 또는 역 이름을, 도보 구간은 출발지·목적지·승하차 지점을 기준으로 채운다. 대중교통 승하차 전후 도보 구간은 `mode`가 `WALK`인 경로선으로 함께 반환한다. 500m를 초과하는 `WALK` 경로선은 TMAP 보행자 경로 좌표를 우선 사용한다. 500m 이하 연결 도보, 외부 API 장애, 좌표 누락 시 출발·도착 좌표를 잇는 fallback 선을 반환한다. 지도 기본 표시는 `BUS`, `SUBWAY` 같은 대중교통 경로선을 우선하고, `WALK` 경로선은 사용자가 도보 구간 확인을 요청할 때 선택 오버레이로 표시할 수 있다. 왕복 일정처럼 여러 이동 경로가 같은 지도에 겹칠 수 있으므로 클라이언트는 `routeOrder` 기준으로 선택 표시할 수 있다. 결과 화면의 일차별 `약 Nkm`는 해당 일차 `routeLines[].distanceMeters` 합계로 계산하며, 경로선이 없을 때만 장소 좌표 간 직선거리를 임시 fallback으로 사용한다.

Provider 응답의 `distanceMeters`가 누락되거나 0 이하이면 서버는 저장된 polyline 좌표의 Haversine 길이로 보완한다. 따라서 경로선이 존재하는 경우 `routeLines[].distanceMeters`는 필수 정수 값이다.

## 10. 공유 링크 생성

`POST /api/v1/schedules/{scheduleId}/shares`

```json
{
  "expiresInDays": 30
}
```

```json
{
  "id": "share-uuid",
  "token": "share-token",
  "url": "/shared-schedules/share-token",
  "expiresAt": "2026-07-23T12:00:00+09:00"
}
```

`expiresInDays`는 생략할 수 있고 `1~365` 범위다. 서버는 32바이트 난수 토큰을 생성하고 응답에서만 원본 토큰을 반환한다. DB에는 SHA-256 해시만 저장한다. `expiresAt`은 `Asia/Seoul` 오프셋을 포함한 ISO-8601 날짜시간이다.

## 11. 공유 일정 조회

`GET /api/v1/shared-schedules/{token}`

```json
{
  "id": "schedule-uuid",
  "status": "CONFIRMED",
  "readOnly": true,
  "days": [
    {
      "dayNo": 1,
      "stops": [
        {
          "place": {
            "name": "이송도전망대"
          }
        }
      ]
    }
  ]
}
```

일정 생성 응답과 같은 날짜별 상세 일정 데이터를 반환하지만 생성 시점의 `evaluation`은 포함하지 않는다. 만료되거나 폐기된 토큰은 `404 SHARE_LINK_NOT_FOUND`를 반환한다.

## 12. 공유 일정 지도 조회

`GET /api/v1/shared-schedules/{token}/map?dayNo={dayNo}`

```json
{
  "startMarker": {
    "name": "부산역",
    "longitude": 129.0403,
    "latitude": 35.1151
  },
  "endMarker": {
    "name": "부산역",
    "longitude": 129.0403,
    "latitude": 35.1151
  },
  "markers": [],
  "routeLines": []
}
```

응답 구조는 일정 지도 데이터 조회와 동일하다.
만료되거나 폐기된 토큰은 `404 SHARE_LINK_NOT_FOUND`를 반환한다.

## 13. 공유 링크 폐기

`DELETE /api/v1/schedules/{scheduleId}/shares/{shareId}`

요청 본문은 없다.

```http
204 No Content
```

`share_links.revoked_at`을 갱신한다.

## PDF·이미지 저장

별도 백엔드 API를 만들지 않는다. 프론트엔드에서 브라우저 인쇄, `jsPDF`, `html2canvas` 등을 사용해 생성한다.

## 커뮤니티 계약

### C-1. 공통 규칙

**요청자 식별**

인증 도입 전까지 `X-User-Id` 헤더로 요청자를 전달한다. 위 `공통`의 경고를 함께 본다.

**페이징이 두 가지다**

| 방식 | 쓰는 곳 | 파라미터 | 응답 |
| --- | --- | --- | --- |
| 커서 | 피드, 사용자 게시물, 댓글 | `cursor`, `size` | `nextCursor` |
| 오프셋 | 인기 피드, 북마크, 팔로워·팔로잉, 차단, 검색 | `page`, `size` | 없음 |

커서 방식은 응답의 `nextCursor`를 다음 요청의 `cursor`로 그대로 넘긴다. `null`이면 더 없다.
목록이 밀리지 않아 무한 스크롤에 적합하다.

오프셋 방식은 정렬 기준이 증가하는 식별자가 아니어서 커서를 쓸 수 없는 곳에만 쓴다. 인기 피드는
점수 기준, 나머지는 관계를 맺은 시각 기준이며 해당 테이블에 대리키가 없다.

`size`는 1 이상 50 이하이며 기본값은 20이다. 해시태그 자동완성만 기본 10, 최대 30이다.

**정렬 방향**

| 대상 | 정렬 |
| --- | --- |
| 피드·사용자 게시물 | 최신순 (`id` 내림차순) |
| 댓글 | 오래된 순 (`id` 오름차순). 대화 흐름을 따라 읽기 위함 |
| 인기 피드 | 점수 내림차순 |
| 팔로워·팔로잉·차단·북마크 | 최근 순 |
| 사용자 검색 | 닉네임 가나다순 |

**삭제 정책**

게시물과 댓글은 `deleted_at`만 남기고 조회에서 제외한다. 삭제됐거나 **작성자가 탈퇴한** 댓글에
살아 있는 답글이 있으면 목록에서 자리를 유지하되 `author`와 `content`를 `null`로, `deleted`를
`true`로 반환한다. 자리를 남기지 않으면 답글이 부모를 잃고 함께 사라진다. 화면 문구는
클라이언트가 정한다.

**탈퇴한 사용자가 쓴 글과 댓글도 모든 조회에서 빠진다.** 프로필은 `404 USER_NOT_FOUND`인데
글은 계속 보이면 앞뒤가 맞지 않고, 목록에 작성자 정보를 채울 수도 없다. 적용 범위는 피드,
인기 피드, 게시물 상세, 사용자 게시물 목록, 북마크 목록, 댓글 목록이다.

게시물 본인 수정·삭제 경로는 요청자가 곧 작성자라 이 조건을 보지 않는다.

**삭제한 게시물의 해시태그 연결은 물리 삭제한다.** 삭제된 글이 태그 사용 수와 태그 필터
피드에 잡히면 안 되기 때문이다. 본문은 그대로 남으므로 복구할 때 본문에서 다시 뽑아
연결한다. `POST /posts/{postId}/restore` 가 함께 처리한다.

### C-2. 게시물 작성

`POST /api/v1/posts`

```json
{
  "content": "광안리 야경 보러 갔는데 날씨가 좋았어요 #광안리맛집 #부산",
  "mediaList": [
    { "url": "https://example.com/media/1.jpg", "mediaType": "IMAGE", "sortOrder": 0 },
    { "url": "https://example.com/media/2.jpg", "mediaType": "IMAGE", "sortOrder": 1 }
  ],
  "placeTags": [
    { "placeId": 42, "latitude": 35.15320000, "longitude": 129.11860000 }
  ]
}
```

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `content` | O | 본문. **최대 2000자.** 해시태그를 본문 안에 함께 적는다 |
| `mediaList` | O | **한 건 이상 열 건 이하.** 사진·영상 기반 서비스라 빈 게시물을 허용하지 않는다 |
| `mediaList[].url` | O | 최대 2048자 |
| `mediaList[].mediaType` | O | `IMAGE`, `VIDEO` 둘 중 하나. 다른 값은 `MALFORMED_REQUEST` |
| `mediaList[].sortOrder` | O | 표시 순서. 0 이상 |
| `placeTags` | X | 내부 `places`에 등록된 장소만 태그할 수 있다. 최대 열 건 |
| `placeTags[].latitude`·`longitude` | X | 사진 EXIF의 촬영 지점. 없으면 생략한다 |

본문 상한을 두는 이유는 `content` 컬럼이 `text`라 DB가 길이를 막지 않기 때문이다. 상한이
없으면 수 MB 본문이 저장되고 그 글이 실린 피드 응답이 전부 부풀어 오른다. 화면에서 긴 본문을
접는 처리는 클라이언트가 한다. 서버는 항상 본문 전체를 보낸다.

**파일 업로드는 서버가 하지 않는다.** 클라이언트가 저장소에 올린 뒤 URL만 전달한다.
**저장소와 업로드 API는 아직 정해지지 않았다.**

해시태그는 본문에서 뽑는다. `#` 뒤의 한글·영문·숫자·밑줄만 인정하고 공백이나 그 밖의 문자에서
끊는다. 여러 단어를 담으려면 붙여 쓴다(`#광안리맛집`). 소문자로 정규화하며 게시물당 20개까지다.
뽑은 태그는 응답의 `hashtags`에 `#` 없이 이름만 담긴다.

응답은 `C-4`의 게시물 상세와 같은 형태다.

### C-3. 피드 조회

`GET /api/v1/posts`

| 파라미터 | 설명 |
| --- | --- |
| `cursor` | 이전 응답의 `nextCursor`. 첫 페이지는 생략 |
| `size` | 1~50, 기본 20 |
| `feed` | `following`이면 팔로우한 사람의 게시물만. 이때 `X-User-Id`가 필요하다 |
| `placeId` | 이 장소를 태그한 게시물만 |
| `hashtag` | 이 해시태그가 달린 게시물만. `#`은 빼고 보낸다 |

`feed`, `placeId`, `hashtag`는 함께 쓸 수 있으며 모두 만족하는 게시물만 반환한다.
`X-User-Id`가 있으면 요청자가 차단한 사용자의 게시물을 제외한다.

```json
{
  "items": [
    {
      "id": 7,
      "author": { "id": 1, "nickname": "감자", "profileImageUrl": "https://example.com/p/1.jpg" },
      "content": "광안리 야경 보러 갔는데 날씨가 좋았어요",
      "thumbnailUrl": "https://example.com/media/1.jpg",
      "mediaCount": 3,
      "placeName": "광안리해수욕장",
      "likeCount": 12,
      "commentCount": 3,
      "liked": true,
      "bookmarked": false,
      "createdAt": "2026-08-24T18:00:00"
    }
  ],
  "nextCursor": 7
}
```

목록은 축약 응답이다. 미디어는 `sortOrder`가 가장 앞선 한 건만 `thumbnailUrl`로 주고 나머지는
개수만 준다. 장소는 대표 하나의 이름만 준다. 전체가 필요하면 상세를 조회한다.

`liked`·`bookmarked`는 요청자 기준이다. `X-User-Id`가 없으면 둘 다 `false`다. `likeCount`는
모두에게 같고 `liked`만 사람마다 다르다.

### C-4. 게시물 상세

`GET /api/v1/posts/{postId}`

```json
{
  "id": 7,
  "author": { "id": 1, "nickname": "감자", "profileImageUrl": "https://example.com/p/1.jpg" },
  "content": "광안리 야경 보러 갔는데 날씨가 좋았어요",
  "mediaList": [
    { "id": 3, "url": "https://example.com/media/1.jpg", "mediaType": "IMAGE", "sortOrder": 0 }
  ],
  "placeTags": [
    {
      "placeId": 42,
      "placeName": "광안리해수욕장",
      "latitude": 35.15320000,
      "longitude": 129.11860000
    }
  ],
  "hashtags": ["광안리맛집", "부산"],
  "likeCount": 12,
  "commentCount": 3,
  "liked": true,
  "bookmarked": false,
  "createdAt": "2026-08-24T18:00:00",
  "updatedAt": "2026-08-24T18:00:00"
}
```

`hashtags`는 본문에서 뽑아 저장해 둔 태그다. 본문을 다시 파싱하지 않고 이 값을 쓰면 되고,
태그를 눌러 `GET /api/v1/posts?hashtag=` 필터 피드로 넘길 때도 그대로 넘긴다.

`mediaList`는 `sortOrder` 오름차순이다. `placeTags[].latitude`는 촬영 지점이며 알 수 없으면
`null`이다. 지도에 표시할 좌표가 없으면 `placeId`로 장소 상세를 조회한다.

### C-5. 게시물 수정·삭제

`PATCH /api/v1/posts/{postId}`

```json
{
  "content": "광안리 야경 진짜 좋았어요 #부산",
  "mediaList": [
    { "url": "https://example.com/media/1.jpg", "mediaType": "IMAGE", "sortOrder": 0 }
  ],
  "placeTags": [{ "placeId": 42 }]
}
```

**보낸 항목만 바뀐다.** 생략한 항목은 그대로 둔다. 본문만 고치려면 `content`만 보내면 되고,
사진 열 장짜리 글이라도 다시 보낼 필요가 없다.

| 보낸 값 | 결과 |
| --- | --- |
| 항목 생략 | 그대로 둔다 |
| `mediaList` 배열 | 기존 사진을 전부 지우고 보낸 것으로 교체한다. 한 건 이상 열 건 이하 |
| `placeTags` 배열 | 기존 태그를 전부 지우고 보낸 것으로 교체한다 |
| `placeTags: []` | 장소 태그를 모두 없앤다 |
| 세 항목 모두 생략 | `400 INVALID_POST_REQUEST` |

**배열은 통째로 교체한다.** 사진 세 장 중 하나를 빼려면 남길 두 장을 보낸다. 추가·삭제·순서
변경이 모두 같은 방식이라 별도 파라미터를 두지 않았다. `mediaList`는 빈 배열로 보낼 수 없다.
사진 없는 게시물을 허용하지 않기 때문이다.

`content`를 보내면 해시태그를 다시 계산한다. 보내지 않으면 기존 태그를 그대로 둔다.

**교체로 빠진 사진의 실제 파일은 지우지 않는다.** 저장소가 아직 정해지지 않아 지울 수단이
없다. 저장소를 붙일 때 남은 파일을 정리하는 작업이 함께 필요하다.

`DELETE /api/v1/posts/{postId}` — `204 No Content`

바로 지우지 않고 삭제 표시만 남긴다. **30일 안에는 되돌릴 수 있고, 그 뒤에는 실제로 지워진다.**

정리는 매일 04:30(KST) 스케줄러가 한다. 게시물과 함께 미디어, 장소 태그, 댓글, 댓글 좋아요,
좋아요, 저장, 해시태그 연결을 지운다. 기간과 실행 여부는 `app.community.post-purge`로 바꾼다.
스케줄러를 끄면 지운 게시물이 계속 쌓이고 본문과 사진 URL 이 DB 에 남는다.

둘 다 작성자 본인이 아니면 `403 POST_ACCESS_DENIED`를 반환한다.

**내가 지운 게시물** — `GET /api/v1/posts/me/deleted?page=0&size=20`

복구 기한이 남은 것만, 삭제한 시각이 최근인 순으로 준다. 응답 형태는 피드 목록과 같고
`nextCursor`는 항상 `null`이다. 삭제 시각 기준 정렬이라 커서를 만들 수 없다.

**복구** — `POST /api/v1/posts/{postId}/restore`

응답은 게시물 상세와 같다. 삭제할 때 해시태그 연결을 실제로 지우므로, 복구할 때 본문에서
다시 뽑아 연결한다. 그러지 않으면 되살린 글이 태그 필터 피드에서 영영 빠진다.

| 상황 | 응답 |
| --- | --- |
| 지운 적 없는 게시물 | `404 POST_NOT_FOUND` |
| 남의 게시물 | `403 POST_ACCESS_DENIED` |
| 삭제한 지 30일이 지남 | `410 POST_RESTORE_WINDOW_EXPIRED` |

기한이 지난 뒤 정리 스케줄러가 돌기 전까지는 `410`, 정리된 뒤에는 `404`다.

**계정 탈퇴는 되돌릴 수 없다.** 되살릴 수 있는 것은 본인이 지운 게시물뿐이다.

### C-6. 인기 피드

`GET /api/v1/posts/popular?page=0&size=20`

최근 7일 게시물을 `좋아요 + 댓글×2` 점수 내림차순으로 반환한다. 응답 형태는 피드와 같으나
`nextCursor`는 항상 `null`이다. 다음 페이지는 `page`를 올려 요청한다.

기간을 두는 이유는 예전 인기글이 상단을 계속 차지하는 것을 막기 위함이다.

점수 계산식과 기간은 `PostService`의 `POPULAR_FEED_DAYS` 상수와 `PostRepository.findPopularFeed`의
`order by` 절에 있다. 정책을 바꾸면 `PopularFeedTest`가 함께 깨지므로 테스트도 같이 고친다.

### C-7. 좋아요·저장

| 동작 | 요청 |
| --- | --- |
| 좋아요 | `POST /api/v1/posts/{postId}/likes` |
| 좋아요 취소 | `DELETE /api/v1/posts/{postId}/likes` |
| 저장 | `POST /api/v1/posts/{postId}/bookmarks` |
| 저장 해제 | `DELETE /api/v1/posts/{postId}/bookmarks` |

```json
{ "likeCount": 13, "liked": true }
```

```json
{ "bookmarked": true }
```

**멱등하다.** 이미 누른 상태에서 다시 요청해도 개수가 늘지 않고, 누른 적 없는 상태에서 취소해도
줄지 않는다. 클라이언트가 중복 요청을 보내도 안전하다.

저장은 몇 명이 저장했는지 공개하지 않는다. 좋아요를 누른 사용자 목록도 제공하지 않는다.

`GET /api/v1/users/me/bookmarks?page=0&size=20` — 저장한 게시물을 최근 저장 순으로 반환한다.
응답 항목은 피드와 같은 축약 형태다. 저장한 뒤 삭제된 게시물은 제외한다.

### C-8. 댓글

`POST /api/v1/posts/{postId}/comments`

```json
{ "content": "저도 여기 가봤는데 좋았어요", "parentId": 3 }
```

`parentId`를 주면 답글이 된다. **답글에 다시 답글을 달면 `400 INVALID_COMMENT_REQUEST`다.**
응답이 두 단계만 표현하기 때문이다.

`GET /api/v1/posts/{postId}/comments?cursor=&size=20`

```json
{
  "items": [
    {
      "id": 3,
      "author": { "id": 1, "nickname": "감자", "profileImageUrl": null },
      "content": "저도 여기 가봤는데 좋았어요",
      "likeCount": 2,
      "liked": false,
      "createdAt": "2026-08-24T18:02:00",
      "deleted": false,
      "hiddenReason": null,
      "replies": [
        {
          "id": 4,
          "author": { "id": 2, "nickname": "고구마", "profileImageUrl": null },
          "content": "언제 가셨어요?",
          "likeCount": 0,
          "liked": false,
          "createdAt": "2026-08-24T18:03:00",
          "deleted": false,
          "hiddenReason": null,
          "replies": []
        }
      ]
    }
  ],
  "nextCursor": null
}
```

최상위 댓글만 페이징하고 답글은 각 댓글에 모두 담는다. 답글의 `replies`는 항상 비어 있다.

**감춰진 최상위 댓글**은 `author`와 `content`가 `null`이고 `deleted`가 `true`이며, 답글은
그대로 담긴다. 자리를 없애면 답글이 부모를 잃고 함께 사라지기 때문이다.

```json
{
  "id": 3,
  "author": null,
  "content": null,
  "likeCount": 0,
  "liked": false,
  "createdAt": "2026-08-24T18:02:00",
  "deleted": true,
  "hiddenReason": "WITHDRAWN",
  "replies": [ { "id": 4, "author": { "id": 2, "nickname": "고구마" }, "content": "언제 가셨어요?" } ]
}
```

`hiddenReason`은 감춘 이유이며 `deleted`가 `false`면 `null`이다.

| 값 | 뜻 |
| --- | --- |
| `DELETED` | 작성자가 지웠다 |
| `WITHDRAWN` | 작성자가 탈퇴했다 |

**화면 문구는 클라이언트가 정한다.** 서버가 "탈퇴한 사용자입니다" 같은 문구를 들고 있으면
문구를 고칠 때마다 서버를 배포해야 한다.

`PATCH /api/v1/posts/{postId}/comments/{commentId}`

```json
{ "content": "저도 여기 가봤는데 좋았어요" }
```

내용만 바꾼다. 좋아요 수와 답글 관계는 그대로 둔다. 응답은 댓글 한 건이다.

`DELETE /api/v1/posts/{postId}/comments/{commentId}` — `204 No Content`.
작성자 본인이 아니면 `403 COMMENT_ACCESS_DENIED`다. 경로의 `postId`와 댓글의 소속이 다르면
`404 COMMENT_NOT_FOUND`를 반환한다.

댓글 좋아요는 게시물 좋아요와 같은 계약이다.

### C-9. 팔로우·차단

| 동작 | 요청 |
| --- | --- |
| 팔로우 | `POST /api/v1/users/{userId}/follows` |
| 팔로우 취소 | `DELETE /api/v1/users/{userId}/follows` |
| 차단 | `POST /api/v1/users/{userId}/blocks` |
| 차단 해제 | `DELETE /api/v1/users/{userId}/blocks` |

```json
{ "followerCount": 152, "following": true }
```

```json
{ "blocked": true }
```

자기 자신을 팔로우하거나 차단하면 `400`이다. 팔로우도 멱등하다.

**차단하면 서로의 팔로우가 끊긴다.** 차단을 풀어도 되살아나지 않으므로 필요하면 다시 팔로우한다.

차단이 실제로 막는 범위는 다음과 같다. 차단은 상대가 내 계정에 접근하지 못하게 하는 것이지
앱 전체에서 상대를 지우는 것이 아니다.

| 상황 | 동작 |
| --- | --- |
| 차단한 사용자의 게시물 | 피드와 인기 피드에서 제외 |
| 차단한 사용자의 게시물 상세·프로필 | **막지 않는다.** 링크로 직접 열면 보인다 |
| 제3자 게시물에 달린 차단 상대의 댓글 | **막지 않는다.** 그대로 보인다 |
| 차단 관계인 사람의 댓글 작성 | `403 COMMENT_NOT_ALLOWED` |
| 내가 차단한 사람을 내가 팔로우 | `400 FOLLOW_BLOCKED_USER`. 차단을 먼저 풀어야 한다 |
| 나를 차단한 사람이 나를 팔로우 | `200`을 주지만 관계를 만들지 않는다 |

마지막 줄이 성공처럼 보이는 이유는, 거절하면 상대가 차단당한 사실을 알게 되기 때문이다.
같은 이유로 댓글 오류 문구에도 차단을 드러내지 않아 누가 차단했는지 알 수 없다.

목록 조회는 오프셋 페이징이다.

| 요청 | 설명 |
| --- | --- |
| `GET /users/{userId}/followers` | 이 사용자를 팔로우하는 사람들 |
| `GET /users/{userId}/followings` | 이 사용자가 팔로우하는 사람들 |
| `GET /users/me/blocks` | 내가 차단한 사람들 |

```json
{
  "items": [ { "id": 1, "nickname": "감자", "profileImageUrl": null } ],
  "totalCount": 152
}
```

차단 목록에는 `totalCount`가 없다.

### C-10. 프로필

`GET /api/v1/users/{userId}/profile`

```json
{
  "id": 1,
  "nickname": "감자",
  "profileImageUrl": "https://example.com/p/1.jpg",
  "postCount": 12,
  "followerCount": 152,
  "followingCount": 88,
  "following": true,
  "me": false
}
```

`following`은 요청자가 이 사용자를 팔로우 중인지, `me`는 본인 프로필인지다. `X-User-Id`가 없으면
둘 다 `false`다. `me`가 `true`면 북마크 탭을 노출한다.

`GET /api/v1/users/{userId}/posts` — 이 사용자의 게시물. 피드와 같은 커서 방식이다.

| 요청 | 본문 | 설명 |
| --- | --- | --- |
| `PATCH /users/me/nickname` | `{ "nickname": "감자" }` | 최대 10자. 중복이면 `409` |
| `PATCH /users/me/profile-image` | `{ "profileImageUrl": "https://..." }` | 이미 업로드된 URL |
| `DELETE /users/me/profile-image` | — | 사진 제거 |

사진 변경과 제거를 나눈 이유는, 한 요청으로는 "사진을 지운다"와 "사진은 그대로 두고 다른 값만
바꾼다"를 구분할 수 없기 때문이다. 세 요청 모두 갱신된 프로필을 반환한다.

### C-11. 검색

`GET /api/v1/users/search?keyword=감자&page=0&size=20`

닉네임에 검색어가 포함된 사용자를 가나다순으로 반환한다. 대소문자를 구분하지 않는다.
**검색어가 비거나 공백뿐이면 빈 목록을 준다.** 전체 사용자 명단이 노출되지 않게 하기 위함이다.

```json
{ "items": [ { "id": 1, "nickname": "감자", "profileImageUrl": null } ] }
```

`GET /api/v1/hashtags/search?keyword=광&size=10`

앞글자로 시작하는 태그를 사용 수 내림차순으로 반환한다. `#`은 빼고 보낸다.

```json
{ "items": [ { "name": "광안리맛집", "postCount": 1203 } ] }
```

태그에는 띄어쓰기를 넣을 수 없으므로, 사용자가 직접 입력하는 대신 여기서 골라 넣도록 하면
표기가 갈라지지 않는다. 자주 쓰일 태그는 서버가 미리 등록해 둔다.

### C-12. 신고

`POST /api/v1/reports`

```json
{ "targetType": "POST", "targetId": 7, "reason": "광고성 게시물입니다" }
```

`targetType`은 `POST`, `COMMENT`, `USER`다. 대상이 없으면 각각
`POST_NOT_FOUND`, `COMMENT_NOT_FOUND`, `USER_NOT_FOUND`를 반환한다.
같은 대상을 다시 신고하면 `409 ALREADY_REPORTED`다.

```json
{
  "id": 1,
  "targetType": "POST",
  "targetId": 7,
  "status": "PENDING",
  "createdAt": "2026-08-24T18:10:00"
}
```

**접수만 한다. 신고를 확인하거나 처리 상태를 바꾸는 관리자 API는 아직 없다.**

### C-13. 게시물 공유

**공유 API 를 두지 않는다.** 게시물은 누구나 볼 수 있으므로 주소만 있으면 되고, 주소는
클라이언트가 `{서비스 주소}/posts/{postId}` 로 만들 수 있다.

일정 공유(`/schedules/{id}/shares`)와 다른 점은 접근 범위다. 일정은 기본이 비공개라 토큰을
발급하고 폐기하는 절차가 필요하지만, 게시물은 이미 공개라 그 절차가 아무것도 막지 않는다.

공유 횟수를 세거나 "많이 공유된 글"을 만들게 되면 그때 추가한다.

### C-14. 알림

알림은 커뮤니티 전용이 아니다. 알림 도메인은 누가 부르는지 모르고, 받는 사람·종류·대상만
받아 쌓는다. 나중에 일정 같은 다른 도메인에서 알림이 필요해지면 같은 방식으로 부르면 된다.

`GET /api/v1/notifications?cursor=&size=20`

```json
{
  "items": [
    {
      "id": 12,
      "type": "POST_LIKE",
      "actor": { "id": 2, "nickname": "고구마", "profileImageUrl": null },
      "targetType": "POST",
      "targetId": 7,
      "read": false,
      "createdAt": "2026-08-25T11:20:00"
    }
  ],
  "nextCursor": null,
  "unreadCount": 3
}
```

| `type` | 언제 | `targetType` / `targetId` |
| --- | --- | --- |
| `POST_LIKE` | 내 게시물에 좋아요 | `POST` / 게시물 |
| `COMMENT` | 내 게시물에 댓글 | `COMMENT` / 달린 댓글 |
| `COMMENT_REPLY` | 내 댓글에 답글 | `COMMENT` / 달린 답글 |
| `COMMENT_LIKE` | 내 댓글에 좋아요 | `COMMENT` / 댓글 |
| `FOLLOW` | 나를 팔로우 | `USER` / 팔로우한 사람 |

**화면 문구는 클라이언트가 만든다.** 서버는 `type`과 `actor`만 준다. 서버가 "고구마님이
회원님의 게시물을 좋아합니다"를 들고 있으면 문구를 고칠 때마다 배포해야 한다.

`targetId`는 누르면 이동할 곳이다. **대상이 그 사이 지워졌을 수 있으므로** 이동한 화면에서
`404`가 날 수 있다.

**알림이 생기지 않는 경우**

- 내가 한 행동 (내 글에 내가 좋아요)
- 이미 눌러 둔 좋아요를 취소했다가 다시 누름 (실제로 관계가 새로 생길 때만 알린다)
- 받는 사람이 탈퇴함
- 차단 관계라 애초에 행동이 막힘

행동한 사람이 나중에 탈퇴하면 그 알림은 목록에서 빠진다. 보여줄 이름이 없기 때문이다.

**읽음 처리**

| 동작 | 요청 |
| --- | --- |
| 한 건 읽음 | `PATCH /api/v1/notifications/{notificationId}/read` |
| 모두 읽음 | `PATCH /api/v1/notifications/read-all` |
| 안 읽은 수만 조회 | `GET /api/v1/notifications/unread-count` |

한 건 읽음은 알림 전체를, 나머지 둘은 `{ "unreadCount": 3 }`을 반환한다. 이미 읽은 알림을
다시 읽어도 처음 읽은 시각을 유지한다. 남의 알림을 읽으려 하면 `404`다. `403`을 주면 그
알림이 있다는 사실이 드러난다.

**새 알림은 클라이언트가 주기적으로 조회해 받는다.** 서버가 밀어 보내지 않는다.
`GET /notifications/unread-count`를 30초 간격 정도로 부르면 벨 표시가 갱신된다. 목록 응답에도
`unreadCount`가 함께 담기므로, 목록을 여는 순간에는 따로 부르지 않아도 된다.

서버가 연결을 열어두고 밀어 보내는 방식(SSE)이나 브라우저 알림(Web Push)은 쓰지 않는다.
전자는 접속자 수만큼 연결을 유지해야 하고, 후자는 서비스 워커·HTTPS·권한 요청이 필요한 데다
iOS 는 홈 화면 추가가 전제다. 알림이 몇십 초 늦게 뜨는 것은 이 기능에서 문제가 되지 않는다.

### C-15. 커뮤니티 오류 코드

| 코드 | 상태 | 발생 상황 |
| --- | --- | --- |
| `POST_NOT_FOUND` | 404 | 게시물이 없거나 삭제됨 |
| `COMMENT_NOT_FOUND` | 404 | 댓글이 없거나 삭제됨. 경로의 게시물과 소속이 다른 경우 포함 |
| `USER_NOT_FOUND` | 404 | 사용자가 없거나 탈퇴함 |
| `NOTIFICATION_NOT_FOUND` | 404 | 알림이 없거나 남의 알림 |
| `POST_ACCESS_DENIED` | 403 | 남의 게시물을 수정·삭제·복구하려 함 |
| `POST_RESTORE_WINDOW_EXPIRED` | 410 | 삭제한 지 30일이 지난 게시물을 복구하려 함 |
| `COMMENT_ACCESS_DENIED` | 403 | 남의 댓글을 수정·삭제하려 함 |
| `INVALID_COMMENT_REQUEST` | 400 | 답글에 답글을 달거나, 부모 댓글이 다른 게시물의 것 |
| `INVALID_FOLLOW_REQUEST` | 400 | 자기 자신을 팔로우 |
| `INVALID_BLOCK_REQUEST` | 400 | 자기 자신을 차단 |
| `INVALID_FEED_REQUEST` | 400 | 팔로잉 피드인데 `X-User-Id`가 없음 |
| `FOLLOW_BLOCKED_USER` | 400 | 내가 차단한 사용자를 팔로우 |
| `COMMENT_NOT_ALLOWED` | 403 | 차단 관계인 사람의 게시물에 댓글 작성 |
| `NICKNAME_ALREADY_USED` | 409 | 다른 사용자가 쓰는 닉네임 |
| `ALREADY_REPORTED` | 409 | 같은 대상을 다시 신고 |

요청 본문 검증에 실패하면 경로에 따라 아래 코드가 나간다. `fieldErrors`에 어느 필드가
왜 틀렸는지 담긴다.

| 경로 | 코드 |
| --- | --- |
| `/api/v1/posts/{postId}/comments` | `INVALID_COMMENT_REQUEST` |
| `/api/v1/posts` 이하 나머지 | `INVALID_POST_REQUEST` |
| `/api/v1/users` 이하 | `INVALID_USER_REQUEST` |

열거형에 없는 값을 보내면 `MALFORMED_REQUEST`이며, `fieldErrors`가 해당 필드와 쓸 수 있는
값을 알려준다.

```json
{
  "code": "MALFORMED_REQUEST",
  "fieldErrors": [
    { "field": "mediaList.mediaType", "message": "값 \"GIF\" 을(를) 쓸 수 없습니다. 가능한 값: IMAGE, VIDEO" }
  ]
}
```

### C-16. 미구현

| 항목 | 상태 |
| --- | --- |
| 이미지·영상 업로드 | 저장소와 API 미정. 현재는 클라이언트가 올린 URL만 받는다 |
| 사진 EXIF 좌표 추출 | 클라이언트가 좌표를 직접 넘긴다 |
| 신고 처리 관리자 기능 | 없다 |

## 공통 오류 응답

```json
{
  "code": "INVALID_SCHEDULE_CONDITION",
  "message": "일정 조건이 올바르지 않습니다.",
  "fieldErrors": [
    {
      "field": "selectedAnswers",
      "message": "필수 질문에 대한 답변이 없습니다: COMPANION, MOBILITY"
    }
  ],
  "traceId": "01J..."
}
```

`fieldErrors[].field`는 요청 본문의 JSON 경로다. `startLocation.longitude`,
`selectedAnswers[questionId=THEME]`, `days[0].dayNo` 처럼 문제가 된 위치를 가리킨다.
빈 배열이면 특정 필드로 원인을 좁힐 수 없는 실패다.

요청 검증에서 자주 나오는 사유는 다음과 같다.

| field | 사유 |
| --- | --- |
| `selectedAnswers` | 필수 질문 답변 누락. `GET /trip-questions`의 `required=true` 질문은 모두 보내야 한다 |
| `selectedAnswers[questionId=...]` | 해당 질문의 답변이 아니거나 선택 개수가 `minSelections`~`maxSelections`를 벗어남 |
| `startLocation.longitude` / `.latitude` | 좌표가 WGS84 범위를 벗어남. 다른 좌표계(예: Naver TM128) 값을 그대로 보낸 경우 |
| `endDate` | 종료일이 시작일보다 빠르거나 여행 기간이 최대 4일을 초과 |
| `mustVisitPlaceIds` | 개수 초과, 중복, 또는 1 미만 ID |

| HTTP | 오류 코드 | 상황 |
| --- | --- | --- |
| 400 | `INVALID_SCHEDULE_CONDITION` | 일정 조건 또는 요청값이 잘못됨 |
| 400 | `INVALID_PLACE_SEARCH_REQUEST` | 장소·위치 검색 파라미터가 잘못됨 |
| 400 | `MALFORMED_REQUEST` | 요청 본문이 JSON으로 읽히지 않음 |
| 404 | `SCHEDULE_NOT_FOUND` | 일정을 찾을 수 없음 |
| 404 | `PLACE_NOT_FOUND` | 장소를 찾을 수 없음 |
| 404 | `SHARE_LINK_NOT_FOUND` | 공유 링크가 없거나 폐기됨 |
| 404 | `RESOURCE_NOT_FOUND` | 존재하지 않는 경로 |
| 422 | `TRANSIT_ROUTE_NOT_FOUND` | 장소 사이 대중교통 경로를 찾지 못함 |
| 501 | `FACILITY_TYPE_NOT_SUPPORTED` | 지원하지 않는 편의시설 유형 |
| 503 | `EXTERNAL_PROVIDER_UNAVAILABLE` | 외부 서비스가 응답하지 않음 |
| 500 | `INTERNAL_ERROR` | 서버가 처리하지 못한 예외 |

OpenAPI 문서(`/v3/api-docs`, Swagger UI)에도 오퍼레이션별로 가능한 오류 응답과 `code` 목록이 함께
노출된다. 컨트롤러마다 애노테이션을 붙이지 않고 공통 커스터마이저가 일괄 적용하므로, 엔드포인트가
추가돼도 문서가 자동으로 따라온다.

**모든 오류는 위 형태를 지킨다.** 깨진 JSON 본문, 잘못된 형식의 경로 변수(`/schedules/abc`),
필수 쿼리 파라미터 누락, 존재하지 않는 경로, 예상하지 못한 예외까지 전부 `code`·`fieldErrors`·`traceId`를
담아 반환한다. 클라이언트는 HTTP 상태가 아니라 `code`로 분기해도 된다.

`INTERNAL_ERROR` 응답에는 내부 예외 메시지를 담지 않는다. 원인은 서버 로그에 같은 `traceId`로 남으므로,
문의 시 `traceId`를 함께 전달하면 된다.

장소·위치 검색 오류 예시:

| field | 사유 |
| --- | --- |
| `size` | `1 이상 50 이하여야 합니다. 요청 값: 1000` |
| `scope` | `INTERNAL 또는 ALL 이어야 합니다.` |
| `keyword` | `keyword 또는 longitude·latitude 중 하나는 있어야 합니다.` |
| `keyword` | `keyword 검색과 좌표 검색은 함께 사용할 수 없습니다.` |
| `longitude` / `latitude` | `좌표 검색에는 longitude와 latitude가 모두 필요합니다.` |

## 1차 스프린트 제외

- 인증과 사용자 도메인
- 편집 토큰과 일정 `version`
- 예산 계산
- 날씨 대응
- ATM과 공중화장실 검색
- 오디오 가이드와 두루누비
