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
| 인증 | 1차 스프린트에서는 인증 없음 |
| 좌표 | WGS84, `longitude`는 경도, `latitude`는 위도 |
| 향후 계획 | 사용자 도메인 도입 후 JWT와 사용자별 일정 조회 적용 |

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

서버는 source, 좌표 범위, 필수 필드를 검증하고 `(source, external_content_id)` 기준으로 upsert한다. 신규·기존 여부와 관계없이 `200 OK`로 내부 장소를 반환한다.

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

기본 생성 화면의 필수 여행 입력은 `startDate`, `endDate`, `startLocation`이다. 질문 단계가 끝난 뒤에는 활성 필수 질문을 모두 포함한 `selectedAnswers`도 필수다. 현재 기본 화면은 `startTime`, `endConstraint`, `customPrompt`를 `null` 또는 생략하고 `lodgingPlan.mode=UNDECIDED`, `fixedEvents=[]`, `dayOverrides=[]`를 전달한다. 숙소·종료 제약·행사·일차별 조정·자유 요청은 API는 지원하지만 프론트 1차 범위에서는 `Deferred`다.

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

`GET /api/v1/schedules`는 V2부터 `startDate ASC`, 같은 시작일은 `createdAt DESC` 순서로 반환한다. 인증 도입 전까지 전체 일정 반환 정책은 유지한다.

### V2-8. V2 오류·충돌 코드

Preview의 사용자 수정 가능 충돌은 HTTP 오류가 아니라 `201 Created`, `status=REQUIRES_ACTION`, `canGenerate=false`, `conflicts[]`로 반환한다. 형식·관계가 잘못돼 Preview 자체를 만들 수 없는 요청만 `400`을 반환한다. Planner의 실제 경로 계산에서 새로 확인된 실행 불가능 조건은 `422`를 반환한다.

| 전달 위치 | HTTP | 오류·충돌 코드 | 상황 |
| --- | ---: | --- | --- |
| HTTP 오류 | 400 | `INVALID_SCHEDULE_PREVIEW_REQUEST` | Preview 필드·관계 검증 실패 |
| HTTP 오류 | 400 | `FIXED_BASE_LOCATION_REQUIRED` | `FIXED_BASE` 위치 누락 |
| HTTP 오류 | 400 | `PER_NIGHT_LOCATION_MISSING` | 숙박일 위치 누락 |
| HTTP 오류 | 400 | `MUST_VISIT_PLACE_LIMIT_EXCEEDED` | 필수 장소 상한 초과 |
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
`evaluation`은 생성 시점에만 계산되는 값이므로 목록 응답에서는 생략한다.

```json
{
  "items": [
    {
      "id": "schedule-uuid",
      "status": "CONFIRMED",
      "startDate": "2026-06-23",
      "endDate": "2026-06-25",
      "styleSummary": "부모님과 함께하는 로컬 중심 일정",
      "days": [
        {
          "dayNo": 1,
          "date": "2026-06-23",
          "stops": [
            {
              "id": "stop-uuid",
              "place": {
                "id": 101,
                "name": "이송도전망대"
              },
              "inboundTransit": {
                "totalMinutes": 25
              }
            }
          ]
        }
      ]
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
  "externalContentId": "126508",
  "contentTypeId": "12",
  "name": "이송도전망대",
  "address": "부산 서구 암남동",
  "longitude": 129.047956,
  "latitude": 35.075519,
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

## 공통 오류 응답

```json
{
  "code": "INVALID_SCHEDULE_CONDITION",
  "message": "일정 조건이 올바르지 않습니다.",
  "fieldErrors": [],
  "traceId": "01J..."
}
```

| HTTP | 오류 코드 | 상황 |
| --- | --- | --- |
| 400 | `INVALID_SCHEDULE_CONDITION` | 일정 조건 또는 요청값이 잘못됨 |
| 404 | `SCHEDULE_NOT_FOUND` | 일정을 찾을 수 없음 |
| 404 | `PLACE_NOT_FOUND` | 장소를 찾을 수 없음 |
| 404 | `SHARE_LINK_NOT_FOUND` | 공유 링크가 없거나 폐기됨 |
| 422 | `TRANSIT_ROUTE_NOT_FOUND` | 장소 사이 대중교통 경로를 찾지 못함 |
| 501 | `FACILITY_TYPE_NOT_SUPPORTED` | 지원하지 않는 편의시설 유형 |
| 503 | `EXTERNAL_PROVIDER_UNAVAILABLE` | 외부 서비스가 응답하지 않음 |

## 1차 스프린트 제외

- 인증과 사용자 도메인
- 편집 토큰과 일정 `version`
- 예산 계산
- 날씨 대응
- ATM과 공중화장실 검색
- 오디오 가이드와 두루누비
