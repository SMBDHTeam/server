# 프론트엔드 일정 생성 V2 연동 가이드

> 상태: 백엔드 구현 완료, 프론트 연동 가능
>
> `POST /api/v1/schedules`의 V2 호출에는 `Idempotency-Key`가 필수다. 헤더가 없으면 서버가 기존 V1 요청으로 해석한다.

## 1. 프론트가 알아야 할 변경점

- 생성은 `Preview 생성 → 사용자 확인 → Planner 실행` 두 단계다.
- 프론트가 일차별 조건을 임의 계산하지 않는다.
- `dailyStartTime`, `dailyEndTime`, 생성 요청의 `days`는 V2에서 사용하지 않는다.
- 숙소가 없는 다일 일정도 정상 생성할 수 있다.
- 외부 검색 장소는 Resolve한 뒤 받은 내부 `placeId`만 Preview에 전달한다.
- 자유 요청은 소프트 선호이며 행사·종료시각보다 우선하지 않는다.
- 일정 생성 요청에는 `Idempotency-Key`가 필수다.

## 2. 권장 페이지 흐름

```text
/trips/new/date
→ /trips/new/step1
→ /trips/new/step2
→ /trips/new/step3
→ /trips/new/places
→ /trips/new/preview
→ /trips/new/generating
→ /trips/{scheduleId}
```

| 페이지 | 수집·표시 내용 | 호출 API |
| --- | --- | --- |
| `/trips/new/date` | 시작일, 종료일, 시작 위치. 위치 버튼에서 검색·지도 모달 표시 | 위치 검색 |
| `/trips/new/step1~3` | 질문 타입에 따른 단일·다중 답변 | `GET /trip-questions` |
| `/trips/new/places` | 내부·Kakao 통합 검색과 선택 | `GET /places`, `POST /places/resolve` |
| `/trips/new/preview` | 서버 계산 결과, 기본값, 경고, 충돌 | Preview 생성·조회 |
| `/trips/new/generating` | 중복 클릭 차단, 생성 진행 | `POST /schedules` |
| `/trips/{scheduleId}` | 생성된 상세 일정 | 일정 단건·지도 API |

현재 기본 흐름에는 GPS 현재 위치, 별도 `/locations`, `/constraints` 단계가 없다. 숙소, 종료 제약, 고정 행사, 일차별 조정, 자유 요청은 API 계약을 유지하되 고급 기능으로 연기한다.

## 3. 화면 입력 정책

### 3.1 날짜와 시작시각

- 날짜는 최대 4일만 선택할 수 있다.
- 시작 위치는 필수이며 검색 버튼을 누르면 지도·검색 모달을 연다.
- GPS 현재 위치 기능은 제공하지 않는다.
- 현재 기본 화면은 시작시각을 받지 않고 `null` 또는 생략해서 전달한다.
- 실제 기본값은 서버 Preview 결과를 사용한다.

기본 화면에서 Preview에 전달하는 최소 여행 입력은 `startDate`, `endDate`, `startLocation` 세 가지다. 다만 Preview 실행 전 질문 단계에서 활성 필수 질문의 `selectedAnswers`도 모두 수집해야 한다.

### 3.2 숙소

현재 기본 화면에서는 숙소를 묻지 않고 `lodgingPlan.mode=UNDECIDED`를 사용한다. 아래 입력은 고급 기능을 추가할 때 사용한다.

```text
○ 아직 정하지 않았어요
○ 모든 날 같은 숙소예요
○ 날짜마다 숙소가 달라요
```

- `UNDECIDED`: 위치 입력을 요구하지 않는다.
- `FIXED_BASE`: 숙소 한 곳을 요구한다.
- `PER_NIGHT`: 시작일부터 종료일 전날까지 각 숙박일을 표시한다.
- 출발 위치를 숙소로 자동 복사하지 않는다.

### 3.3 종료 제약

현재 기본 화면에서는 `endConstraint=null`을 사용한다. 고급 기능에서는 “도착 위치” 하나로 숙소와 교통편을 같이 받지 않는다.

```text
마지막에 도착해야 할 곳이 있나요?
○ 없음
○ 특정 시각까지 도착
○ 기차 출발
○ 항공편 출발
```

- 교통편 시각과 도착 마감시각을 구분해서 표시한다.
- 기본 여유시간을 입력 컨트롤에 노출한다.
- Preview가 반환한 `appliedBufferMinutes`를 확인 화면에 표시한다.

### 3.4 일차별 조정

현재 기본 화면에서는 `dayOverrides=[]`를 사용한다. 고급 기능에서도 일차별 폼을 기본 화면에 모두 노출하지 않고, “일차별 시간·장소 조정”을 펼친 사용자만 편집한다.

- override 식별자는 `date`다.
- 사용자가 입력하지 않은 필드는 요청에서 생략한다.
- 날짜 범위가 바뀌면 기존 override를 자동 이동하지 않고 재확인을 요구한다.
- 마지막 날에 `endConstraint`가 있으면 `dayOverrides.endLocation` 입력을 비활성화한다.

## 4. 클라이언트 상태 모델

권장 위치는 `/trips/new/layout.tsx` 아래 Provider다. 새로고침 복원을 위해 비밀값이 없는 Draft만 `sessionStorage`에 저장한다.

```ts
type LocationInput = {
  name: string;
  address?: string | null;
  longitude: number;
  latitude: number;
};

type SelectedAnswerInput = {
  questionId: string;
  answerIds: string[];
};

type LodgingPlanInput =
  | { mode: "UNDECIDED" }
  | { mode: "FIXED_BASE"; baseLocation: LocationInput }
  | {
      mode: "PER_NIGHT";
      nightStays: Array<{ date: string; location: LocationInput }>;
    };

type EndConstraintInput = {
  type: "ARRIVE_BY" | "TRAIN_DEPARTURE" | "FLIGHT_DEPARTURE";
  location: LocationInput;
  targetAt: string;
  bufferMinutes?: number;
};

type DayOverrideInput = {
  date: string;
  availableFrom?: string;
  availableUntil?: string;
  startLocation?: LocationInput;
  endLocation?: LocationInput;
};

type FixedEventInput = {
  clientEventId: string;
  name: string;
  placeId: number;
  startsAt: string;
  endsAt: string;
};

type TripDraft = {
  startDate?: string;
  endDate?: string;
  startLocation?: LocationInput;
  startTime?: string;
  lodgingPlan: LodgingPlanInput;
  endConstraint?: EndConstraintInput;
  selectedAnswers: SelectedAnswerInput[];
  mustVisitPlaceIds: number[];
  fixedEvents: FixedEventInput[];
  dayOverrides: DayOverrideInput[];
  customPrompt?: string;
  previewId?: string;
  previewExpiresAt?: string;
  idempotencyKey?: string;
};

type PreviewConflict = {
  code: string;
  message: string;
  fieldPath?: string | null;
  conflictDate?: string | null;
  requiredMinutes?: number | null;
  availableMinutes?: number | null;
  adjustableFields: string[];
};

type ResolvedDay = {
  date: string;
  availableFrom: string;
  availableUntil: string;
  startLocation: LocationInput | null;
  endLocation: LocationInput | null;
  startLocationSource: "USER" | "LODGING" | "DAY_OVERRIDE" | "PLANNER_DECIDES";
  endLocationSource: "LODGING" | "END_CONSTRAINT" | "DAY_OVERRIDE" | "PLANNER_DECIDES" | "LAST_STOP";
};

type SchedulePreview = {
  previewId: string;
  status: "READY" | "REQUIRES_ACTION" | "EXPIRED" | "CONSUMED";
  canGenerate: boolean;
  expiresAt: string;
  timeZone: "Asia/Seoul";
  lodgingMode: LodgingPlanInput["mode"];
  routeCoverage: "FULL" | "ATTRACTION_ROUTES_ONLY";
  resolvedDays: ResolvedDay[];
  appliedDefaults: Array<{
    fieldPath: string;
    resolvedValue: unknown;
    reasonCode: string;
  }>;
  interpretedPrompt: {
    preferences: string[];
    unrecognizedTexts: string[];
    source: "RULE_BASED" | "HYBRID_AI" | "FALLBACK";
    confidence: number;
  };
  warnings: Array<{ code: string; date?: string | null; message: string }>;
  conflicts: PreviewConflict[];
  scheduleId?: string;
};
```

Draft가 변경되면 기존 `previewId`와 `idempotencyKey`를 폐기한다. 변경된 Draft로 새로운 Preview를 만들어야 한다.

## 5. API 호출 순서

### 5.1 질문 조회

```http
GET /api/v1/trip-questions
```

- `type=SINGLE_CHOICE`: 라디오 UI
- `type=MULTIPLE_CHOICE`: 체크박스 UI
- `minSelections`, `maxSelections`로 선택 수 검증
- `uiStep`으로 Step1~3 질문을 그룹화한다.
- 질문·답변 ID는 저장 요청과 디자인 분기에 사용하는 안정적인 계약이다. 질문 문구와 일반 옵션 렌더링은 API 응답을 사용한다.

| Step | `uiStep` | 질문 ID | 활성 답변 ID |
| --- | ---: | --- | --- |
| 1 | 1 | `COMPANION` | `COMPANION_SOLO`, `COMPANION_FRIENDS`, `COMPANION_COUPLE`, `COMPANION_FAMILY_WITH_CHILD`, `COMPANION_PARENTS`, `COMPANION_OTHER` |
| 1 | 1 | `MOBILITY` | `MOBILITY_NORMAL`, `MOBILITY_LOW_WALK` |
| 2 | 2 | `PACE` | `PACE_PACKED`, `PACE_RELAXED` |
| 2 | 2 | `TRANSIT` | `TRANSIT_SIMPLE`, `TRANSIT_FAST` |
| 3 | 3 | `THEME` | `THEME_FOOD`, `THEME_NATURE`, `THEME_HISTORY_CULTURE`, `THEME_SEA`, `THEME_SHOPPING`, `THEME_HEALING` |

Step2 표시 문구는 `PACE_PACKED=빼곡하고 알찬 일정`, `PACE_RELAXED=널널하고 여유로운 일정`, `TRANSIT_SIMPLE=환승은 적게`, `TRANSIT_FAST=빠른 이동 우선`이다.

### 5.2 장소 검색과 Resolve

```http
GET /api/v1/places?keyword=광안리&scope=ALL&size=20
```

- `placeId`가 있으면 즉시 선택 가능하다.
- `placeId`가 `null`이면 Kakao 외부 후보이므로 선택 시 Resolve한다.

```http
POST /api/v1/places/resolve
Content-Type: application/json
```

Resolve가 성공한 뒤 반환된 `placeId`를 `mustVisitPlaceIds` 또는 `fixedEvents[].placeId`에 저장한다. 외부 후보의 임시 ID를 Preview에 전달하지 않는다.

검색 카드와 담은 장소는 `category`, `address`, `primaryImageUrl`을 사용한다. 필드는 응답 객체에 항상 존재하지만 원천 데이터에 값이 없으면 `null`일 수 있다. `/api/places/images`는 Mock 보완용 프론트 BFF이며 운영 계약이 아니다.

### 5.3 Preview 생성

```http
POST /api/v1/schedule-previews
Content-Type: application/json
```

성공 응답이 `READY`이면 확인 화면을 표시한다. `REQUIRES_ACTION`이면 `conflicts[].fieldPath`에 해당하는 입력 화면으로 이동할 수 있어야 한다.

현재 기본 화면은 다음 선택값을 사용한다. JavaScript에서 `undefined` 필드를 생략해도 서버 의미는 `null`과 같다.

```json
{
  "startTime": null,
  "lodgingPlan": { "mode": "UNDECIDED" },
  "endConstraint": null,
  "fixedEvents": [],
  "dayOverrides": [],
  "customPrompt": null
}
```

### 5.4 Preview 복원

```http
GET /api/v1/schedule-previews/{previewId}
```

- Preview 페이지 새로고침 시 사용한다.
- `410 PREVIEW_EXPIRED`이면 Draft는 유지하고 Preview만 다시 생성한다.
- `CONSUMED`이면 응답의 `scheduleId`로 이동한다.

### 5.5 일정 생성

Preview마다 UUID 멱등성 키를 한 번 생성해 `sessionStorage`에 저장한다.

```http
POST /api/v1/schedules
Idempotency-Key: 9bd292fd-5f2a-4ce4-9002-7ac511cdd4ea
Content-Type: application/json

{
  "previewId": "preview-uuid"
}
```

- 요청 시작과 동시에 생성 버튼을 비활성화한다.
- 네트워크 재시도는 같은 멱등성 키를 사용한다.
- Draft 또는 Preview가 바뀌면 새 키를 만든다.
- 성공하면 Draft를 삭제하고 일정 상세로 이동한다.

### 5.6 일정 상세 복원

```http
GET /api/v1/schedules/{scheduleId}
GET /api/v1/schedules/{scheduleId}/map
```

- 상세 페이지는 생성 응답을 메모리에만 보관하지 않는다.
- 직접 접근과 새로고침에서 일정 단건 API를 다시 호출한다.
- 숙소 미정 일정은 nullable 시작·종료 마커를 정상 상태로 처리한다.
- 일차별 카드에는 `dayNo`, `stops[].order`, `arriveAt`, `stayMinutes`, 장소명·분류·좌표를 사용한다.
- `stops[].mealTimeSlot`이 `LUNCH` 또는 `DINNER`이면 점심·저녁 추천 배지를 표시하고, `waitingMinutesBefore>0`이면 식사 시간까지 남은 여유 시간을 안내한다.
- 지도는 `markers[].dayNo/order/longitude/latitude`와 `routeLines[].coordinates`를 사용한다.
- 결과의 `약 Nkm`는 선택 일차의 `routeLines[].distanceMeters` 합계다. 경로선이 전혀 없을 때만 장소 좌표 간 직선거리를 임시값으로 사용한다.
- 지도 제공자에 의존하지 말고 WGS84 `[longitude, latitude]` 계약을 기준으로 렌더링한다.
- 일차를 바꿀 때 `GET /schedules/{id}/map?dayNo=N`을 다시 호출하고 `startMarker`, `endMarker`를 방문지 마커와 구분한다.
- 장소 사이에는 `inboundTransit`의 노선명, 전체·도보시간, 환승, 요금, 승하차 지점을 표시한다.
- `provider=FAKE|UNKNOWN`, `fallbackUsed=true`, `realtimeStatus=UNAVAILABLE`이면 “예상 이동시간”으로 표시한다.
- 종료 제약·숙소가 없으면 생성 결과의 종료지는 마지막 방문지이며 `endLocationSource=LAST_STOP`, `finalTransit=null`이다.

## 6. Preview 확인 화면

반드시 보여줄 정보는 다음과 같다.

- 여행 기간과 서버 기준 시간대
- 일차별 활동 가능 시간
- 확정된 일차별 시작·종료 위치
- 숙소 미정으로 Planner가 결정할 위치
- 항공·기차 여유시간
- 적용된 기본값과 이유
- 필수 방문 장소
- 고정 행사
- 적용된 질문 답변
- 자유 요청 해석 결과와 해석하지 못한 문장
- 경고와 생성 차단 충돌

현재 서버가 점수에 반영하는 자유 요청 정규화 값은 `LOW_WALKING`, `PREFER_SEA_VIEW`, `PREFER_FOOD`다. `unrecognizedTexts`가 있으면 적용된 조건처럼 표시하지 말고 사용자가 구조화된 장소·행사 입력으로 옮길 수 있게 안내한다.

`canGenerate=false`이면 생성 버튼을 비활성화한다. warning만 있는 경우에는 사용자가 확인하고 생성할 수 있다.

## 7. 숙소 미정 결과 표시

숙소 미정은 실패나 불완전한 저장 상태가 아니다. 다만 실제 계산 범위를 정확히 표시해야 한다.

```text
숙소가 정해지지 않아 숙소↔관광지 이동시간은 포함되지 않았습니다.
방문지 간 동선을 기준으로 만든 초안 일정입니다.
```

- `routeCoverage=ATTRACTION_ROUTES_ONLY` 배지를 표시한다.
- 존재하지 않는 숙소 경로를 지도에 그리지 않는다.
- “숙소 추가 후 다시 만들기” 액션을 제공할 수 있다.

## 8. 오류 처리

Preview 응답의 `status=REQUIRES_ACTION`은 HTTP 오류가 아니다. `conflicts[]`를 입력 화면과 연결해 수정하도록 안내한다.

| 전달 위치·코드 | 사용자 처리 |
| --- | --- |
| `400 INVALID_SCHEDULE_PREVIEW_REQUEST` | 필드별 오류 표시 |
| `400 FIXED_BASE_LOCATION_REQUIRED` | 숙소 입력 화면으로 이동 |
| `400 PER_NIGHT_LOCATION_MISSING` | 누락 숙박일 강조 |
| `400 MUST_VISIT_PLACE_LIMIT_EXCEEDED` | 선택 가능한 남은 개수 표시 |
| `409 IDEMPOTENCY_KEY_REUSED` | 새 키를 만들기 전에 현재 Draft·Preview 일치 여부 확인 |
| `409 PREVIEW_ALREADY_CONSUMED` | 제공된 `scheduleId`로 이동 |
| `409 SCHEDULE_CREATION_IN_PROGRESS` | 같은 키를 유지하고 잠시 후 상태 재시도 |
| `410 PREVIEW_EXPIRED` | Draft로 Preview 재생성 |
| Preview `INSUFFICIENT_AVAILABLE_TIME` | 해당 날짜와 필요한 최소시간 표시 |
| Preview `FIXED_EVENT_CONFLICT` | 충돌 행사 두 개 표시 |
| `422 FIXED_EVENT_UNREACHABLE` | 행사 전후 장소·이동시간 안내 |
| `422 END_CONSTRAINT_UNREACHABLE` | 마지막 방문지 축소 또는 종료 제약 변경 안내 |
| `503 EXTERNAL_PROVIDER_UNAVAILABLE` | 같은 멱등성 키로 재시도 제공 |

서버 `message`만 문자열로 표시하지 말고 `fieldErrors`, `conflictDate`, `adjustableFields`를 화면 이동과 강조에 사용한다.

## 9. 로딩과 재시도

- Preview 생성과 Planner 실행 로딩을 구분한다.
- Preview 로딩 문구: “입력한 조건을 확인하고 있어요.”
- Planner 로딩 문구: “이동시간과 방문 순서를 계산하고 있어요.”
- 생성 요청 중 페이지 이탈 경고를 표시할 수 있다.
- 네트워크 오류 재시도 시 같은 멱등성 키를 유지한다.
- HTTP `422`는 재시도하지 않고 사용자 입력 변경을 요구한다.

## 10. API 모듈 권장 구조

```text
src/lib/api/
├── client.ts
├── questions.ts
├── locations.ts
├── places.ts
├── schedule-previews.ts
├── schedules.ts
└── shares.ts

src/types/api/
├── common.ts
├── place.ts
├── schedule-preview.ts
└── schedule.ts

src/store/
└── trip-draft.tsx
```

- API DTO와 화면 모델을 분리한다.
- 브라우저 요청은 `/api/v1` 상대 경로와 프록시를 사용한다.
- 날짜시간은 ISO-8601 문자열을 그대로 보존하고 임의로 브라우저 로컬 시간대로 변환하지 않는다.
- 일정 시간대는 Preview의 `timeZone`을 사용한다.

## 11. 프론트 E2E 시나리오

| ID | 시나리오 | 기대 결과 |
| --- | --- | --- |
| FE-V2-01 | 선택한 시작 위치에서 당일 생성 | 서버 기본 시작시각 확인 후 상세 이동 |
| FE-V2-02 | 숙소 미정 3박 4일 | warning 확인 후 생성 가능 |
| FE-V2-03 | 질문 단계 이동 | `uiStep`별 질문과 선택 수 검증 |
| FE-V2-04 | 필수 장소 선택 | 내부 장소 ID를 Preview에 전달 |
| FE-V2-05 | 결과 지도 | 서버 경로선 표시와 거리 합계 일치 |
| FE-V2-06 | 다중 테마 답변 | 선택 수 검증 후 Preview 반영 |
| FE-V2-07 | Kakao 외부 장소 선택 | Resolve 후 내부 `placeId` 전달 |
| FE-V2-08 | Preview 충돌 | 생성 차단과 수정 화면 이동 |
| FE-V2-09 | Preview 만료 후 새로고침 | Draft 유지 후 Preview 재생성 |
| FE-V2-10 | 생성 버튼 연속 클릭·네트워크 재시도 | 일정 한 건만 생성 |

GPS 현재 위치, 숙소 모드 선택, 항공·기차 종료 제약, 날짜별 숙소, 고정 행사 충돌, 일차별 시간·장소 조정은 프론트 1차 완료 범위에서 `Deferred`다.

## 12. 프론트 완료 기준

- Mock 질문·장소·일정 데이터를 제거한다.
- Draft를 뒤로 가기와 새로고침 후 복원한다.
- Preview가 바뀌면 이전 생성 요청을 재사용하지 않는다.
- 질문 타입과 선택 수를 API 응답으로 렌더링한다.
- 외부 장소는 Resolve 성공 전 필수 장소로 확정하지 않는다.
- Preview warning과 conflict를 구분한다.
- 숙소 미정 일정의 경로 범위를 명시한다.
- 생성 중 중복 요청을 UI와 멱등성 키로 방지한다.
- 생성된 일정을 상세·지도 화면에서 새로고침 후 조회한다.
- 기본 흐름에서 위치 검색 모달 외의 고급 입력 단계를 요구하지 않는다.
- `Deferred` 기능은 API 지원 여부와 별개로 프론트 완료 판정에서 제외한다.
