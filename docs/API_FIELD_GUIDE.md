# API 데이터 필드 설명

`docs/API_SPEC.md`의 JSON 필드 의미를 설명한다.

일정 생성 V2 필드는 현재 구현 계약이다. 기존 번호 섹션의 V1 일정 생성 필드는 `Idempotency-Key`가 없는 호환 요청에만 사용한다.

| 표기 | 의미 |
| --- | --- |
| O | 반드시 전달하거나 반환하는 값 |
| X | 생략하거나 `null`일 수 있는 값 |
| 조건부 | 특정 요청 방식에서만 필요한 값 |
| object | 여러 필드를 묶은 JSON 객체 |
| array | 같은 종류의 값을 여러 개 담는 JSON 목록 |
| `items[]` | `items` 배열 안의 항목 한 개 |

## 1. 사전 질문 조회

`GET /api/v1/trip-questions`

요청값은 없다.

| 응답 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `items` | array | O | 질문 목록 |
| `items[].id` | string | O | 질문 고정 ID |
| `items[].text` | string | O | 화면에 표시할 질문 |
| `items[].type` | string | O | `SINGLE_CHOICE`, `MULTIPLE_CHOICE` |
| `items[].required` | boolean | O | 필수 질문 여부 |
| `items[].minSelections` | integer | O | 최소 선택 수 |
| `items[].maxSelections` | integer | O | 최대 선택 수 |
| `items[].uiStep` | integer | O | 기본 생성 화면에서 질문을 표시할 단계. `1~3` |
| `items[].displayOrder` | integer | O | 질문 표시 순서 |
| `items[].answers` | array | O | 선택 가능한 답변 목록 |
| `items[].answers[].id` | string | O | 답변 고정 ID |
| `items[].answers[].label` | string | O | 답변 버튼 문구 |
| `items[].answers[].displayOrder` | integer | O | 답변 표시 순서 |

## 2. 출발지·도착지 검색

`GET /api/v1/locations/search`

| 요청 필드 | 위치 | 자료형 | 필수 | 의미 |
| --- | --- | --- | :---: | --- |
| `keyword` | Query | string | O | 검색할 장소명 |
| `size` | Query | integer | X | 최대 결과 수. 생략 시 `10` |

| 응답 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `items` | array | O | Kakao Local 검색 결과 |
| `items[].name` | string | O | 장소명 |
| `items[].address` | string | X | 주소 |
| `items[].longitude` | number | O | 경도 |
| `items[].latitude` | number | O | 위도 |
| `items[].externalId` | string | O | 카카오 장소 ID |
| `items[].source` | string | O | `KAKAO_LOCAL` |

## 일정 생성 V2 필드

### V2-1. 질문 조회 추가 필드

| 응답 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `items[].type` | string | O | `SINGLE_CHOICE`, `MULTIPLE_CHOICE` |
| `items[].minSelections` | integer | O | 최소 선택 수 |
| `items[].maxSelections` | integer | O | 최대 선택 수 |
| `items[].uiStep` | integer | O | Step1~3 질문 그룹 |

`SINGLE_CHOICE`는 `maxSelections=1`이며 필수 질문은 `minSelections=1`, 선택 질문은 `minSelections=0`이다.
질문·답변 ID는 요청 저장과 화면별 디자인 분기에 사용하는 안정적인 계약이며, 화면 그룹은 배열 순서가 아니라 `uiStep`으로 결정한다.

### V2-2. 통합 장소 검색

| 요청 필드 | 위치 | 자료형 | 필수 | 의미 |
| --- | --- | --- | :---: | --- |
| `keyword` | Query | string | O | 장소명 검색어 |
| `scope` | Query | string | X | `INTERNAL`, `ALL`. 기본 `INTERNAL` |
| `size` | Query | integer | X | 기본 `20`, 최소 `1`, 최대 `50` |

| 응답 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `items` | array | O | 내부·외부 장소 후보 |
| `items[].placeId` | integer/null | 조건부 | 내부 장소 ID. 외부 미확정 후보는 `null` |
| `items[].source` | string | O | `TOUR_API`, `KAKAO_LOCAL` |
| `items[].externalId` | string | O | 데이터 출처의 장소 ID |
| `items[].name` | string | O | 장소명 |
| `items[].category` | string/null | X | 표시용 분류 |
| `items[].categoryLabel` | string | O | 카테고리 코드의 사용자 표시 문구 |
| `items[].address` | string/null | X | 주소 |
| `items[].longitude` | number | O | 경도 |
| `items[].latitude` | number | O | 위도 |
| `items[].primaryImageUrl` | string/null | X | 대표 이미지 |
| `items[].resolved` | boolean | O | 내부 `placeId` 확정 여부 |

### V2-3. 외부 장소 Resolve

`POST /api/v1/places/resolve`

| 요청 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `source` | string | O | V2에서는 `KAKAO_LOCAL`만 허용 |
| `externalId` | string | O | Kakao 장소 ID |
| `name` | string | O | 검색 응답 장소명 |
| `category` | string | X | 검색 응답 분류 |
| `address` | string | X | 검색 응답 주소 |
| `longitude` | number | O | 경도 |
| `latitude` | number | O | 위도 |
| `placeUrl` | string | X | 외부 장소 페이지 URL |

| 응답 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `placeId` | integer | O | upsert 후 내부 장소 ID |
| `source` | string | O | `KAKAO_LOCAL` |
| `externalId` | string | O | Kakao 장소 ID |
| `name` | string | O | 저장된 장소명 |
| `category` | string/null | X | 저장된 카테고리 코드 또는 원문 |
| `categoryLabel` | string | O | 사용자 표시용 카테고리 |
| `address` | string/null | X | 저장된 주소 |
| `longitude` | number | O | 저장된 경도 |
| `latitude` | number | O | 저장된 위도 |
| `primaryImageUrl` | string/null | X | 대표 이미지 URL |
| `placeUrl` | string/null | X | 외부 장소 페이지 URL |
| `resolved` | boolean | O | 항상 `true` |
| `operatingInfoAvailable` | boolean | O | 운영정보 보유 여부 |

### V2-4. Preview 생성 요청

`POST /api/v1/schedule-previews`

| 요청 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `startDate` | string(date) | O | 여행 시작일 |
| `endDate` | string(date) | O | 여행 종료일. 최대 4일 |
| `startLocation` | object | O | 첫날 여행 시작 위치 |
| `startLocation.name` | string | O | 시작 위치명 |
| `startLocation.longitude` | number | O | 시작 위치 경도 |
| `startLocation.latitude` | number | O | 시작 위치 위도 |
| `startTime` | string(time) | X | 첫날 여행 시작 가능시각 |
| `lodgingPlan` | object | O | 숙소 계획. 숙소 미정이면 `{"mode":"UNDECIDED"}` |
| `lodgingPlan.mode` | string | O | `UNDECIDED`, `FIXED_BASE`, `PER_NIGHT` |
| `lodgingPlan.baseLocation` | object | 조건부 | `FIXED_BASE`일 때 필수 |
| `lodgingPlan.nightStays` | array | 조건부 | `PER_NIGHT`일 때 필수 |
| `lodgingPlan.nightStays[].date` | string(date) | O | 숙박하는 밤의 날짜 |
| `lodgingPlan.nightStays[].location` | object | O | 해당 날짜 숙소 위치 |
| `endConstraint` | object | X | 마지막 도착·교통편 제약 |
| `endConstraint.type` | string | O | `ARRIVE_BY`, `TRAIN_DEPARTURE`, `FLIGHT_DEPARTURE` |
| `endConstraint.location` | object | O | 도착해야 하는 위치 |
| `endConstraint.targetAt` | string(date-time) | O | 오프셋을 포함한 목표시각 |
| `endConstraint.bufferMinutes` | integer | X | 목표시각 전 확보할 시간. 0 이상 |
| `selectedAnswers` | array | O | 활성 필수 질문별 선택 답변 |
| `selectedAnswers[].questionId` | string | O | `questions.id` |
| `selectedAnswers[].answerIds` | array | O | 질문별 선택한 `answers.id` 목록 |
| `selectedAnswers[].answerIds[]` | string | O | 중복 없는 답변 ID |
| `mustVisitPlaceIds` | array | X | Resolve가 끝난 내부 장소 ID 목록. 여행 일수당 최대 5개 |
| `mustVisitPlaceIds[]` | integer | O | 내부 `places.id` |
| `fixedEvents` | array | X | 반드시 배치할 고정 행사 |
| `fixedEvents[].clientEventId` | string | O | Draft 안에서 행사를 식별하는 클라이언트 ID |
| `fixedEvents[].name` | string | O | 행사·공연명 |
| `fixedEvents[].placeId` | integer | O | Resolve가 끝난 내부 장소 ID |
| `fixedEvents[].startsAt` | string(date-time) | O | 오프셋 포함 행사 시작시각 |
| `fixedEvents[].endsAt` | string(date-time) | O | 오프셋 포함 행사 종료시각 |
| `dayOverrides` | array | X | 특정 날짜 기본 조건 변경 |
| `dayOverrides[].date` | string(date) | O | 여행 범위 안의 실제 날짜 |
| `dayOverrides[].availableFrom` | string(time) | X | 해당 날짜 여행 시작 가능시각 |
| `dayOverrides[].availableUntil` | string(time) | X | 해당 날짜 일정을 마쳐야 하는 시각 |
| `dayOverrides[].startLocation` | object | X | 해당 날짜 출발 위치 강제값 |
| `dayOverrides[].endLocation` | object | X | 해당 날짜 도착 위치 강제값 |
| `customPrompt` | string | X | 최대 500자의 소프트 선호 요청 |

현재 `interpretedPrompt.preferences`는 `LOW_WALKING`, `PREFER_SEA_VIEW`, `PREFER_FOOD`를 반환할 수 있다. 인식하지 못한 요청은 `unrecognizedTexts`로 돌려주며 Hard Constraint로 적용하지 않는다.
마지막 날에 `endConstraint`가 있으면 같은 날짜의 `dayOverrides[].endLocation`은 함께 전달할 수 없다.

위치 객체는 공통으로 `name`, `longitude`, `latitude`가 필수이며 `address`는 선택이다. 날짜시간은 `Asia/Seoul` 오프셋을 포함한 ISO-8601 형식을 사용한다.

### V2-5. Preview 응답

| 응답 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `previewId` | string(UUID) | O | Preview ID |
| `status` | string | O | `READY`, `REQUIRES_ACTION`, `EXPIRED`, `CONSUMED` |
| `canGenerate` | boolean | O | 현재 Preview로 생성 가능한지 여부 |
| `expiresAt` | string(date-time) | O | Preview 만료시각 |
| `timeZone` | string | O | V2 부산 서비스는 `Asia/Seoul` |
| `lodgingMode` | string | O | 적용된 숙소 모드 |
| `routeCoverage` | string | O | `FULL`, `ATTRACTION_ROUTES_ONLY` |
| `resolvedDays` | array | O | 서버가 확정한 일차별 실행 조건 |
| `resolvedDays[].date` | string(date) | O | 실제 날짜 |
| `resolvedDays[].availableFrom` | string(time) | O | 여행 시작 가능시각 |
| `resolvedDays[].availableUntil` | string(time) | O | 일정을 마쳐야 하는 시각 |
| `resolvedDays[].startLocation` | object/null | X | 확정된 시작 위치 |
| `resolvedDays[].endLocation` | object/null | X | 확정된 종료 위치 |
| `resolvedDays[].startLocationSource` | string | O | `USER`, `LODGING`, `DAY_OVERRIDE`, `PLANNER_DECIDES` |
| `resolvedDays[].endLocationSource` | string | O | `LODGING`, `END_CONSTRAINT`, `DAY_OVERRIDE`, `PLANNER_DECIDES` |
| `resolvedEndConstraint` | object/null | X | 계산된 종료 제약 |
| `resolvedEndConstraint.appliedBufferMinutes` | integer | O | 실제 적용 여유시간 |
| `resolvedEndConstraint.availableUntil` | string(time) | O | 마지막 날 계산 종료시각 |
| `appliedDefaults` | array | O | 서버 적용 기본값 목록 |
| `appliedDefaults[].fieldPath` | string | O | 적용된 응답 필드 경로 |
| `appliedDefaults[].resolvedValue` | string/object | O | 적용값 |
| `appliedDefaults[].reasonCode` | string | O | 기본값 적용 이유 코드 |
| `interpretedPrompt.preferences` | array | O | 정규화된 소프트 선호 코드 |
| `interpretedPrompt.unrecognizedTexts` | array | O | 해석하지 못한 입력 조각 |
| `interpretedPrompt.source` | string | O | `RULE_BASED`, `HYBRID_AI`, `FALLBACK` |
| `interpretedPrompt.confidence` | integer | O | 해석 신뢰도 `0~100` |
| `warnings` | array | O | 확인 후 생성 가능한 경고 |
| `warnings[].code` | string | O | 고정 경고 코드 |
| `warnings[].date` | string(date)/null | X | 경고 대상 날짜 |
| `warnings[].message` | string | O | 사용자 표시 문구 |
| `conflicts` | array | O | 생성 전에 해결해야 하는 충돌 |
| `conflicts[].code` | string | O | 고정 충돌 코드 |
| `conflicts[].message` | string | O | 사용자 표시 문구 |
| `conflicts[].fieldPath` | string/null | X | 수정할 Draft 필드 경로 |
| `conflicts[].conflictDate` | string(date)/null | X | 충돌 날짜 |
| `conflicts[].requiredMinutes` | integer/null | X | 필요한 최소시간 |
| `conflicts[].availableMinutes` | integer/null | X | 현재 가용시간 |
| `conflicts[].adjustableFields` | array | O | 변경 가능한 Draft 필드 경로 |
| `scheduleId` | string(UUID) | 조건부 | `CONSUMED` Preview의 생성 일정 ID |

`canGenerate=false`이면 `status=REQUIRES_ACTION`이고 `conflicts`가 한 건 이상이어야 한다.

### V2-6. Preview 기반 일정 생성

`POST /api/v1/schedules`

| 요청 필드 | 위치 | 자료형 | 필수 | 의미 |
| --- | --- | --- | :---: | --- |
| `Idempotency-Key` | Header | string | O | 요청 중복 방지 키. UUID 권장, 최대 128자 |
| `previewId` | Body | string(UUID) | O | `READY` 상태의 Preview ID |

| 응답 추가 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `previewId` | string(UUID) | O | 실행에 사용한 Preview ID |
| `planningAssumptions` | object | O | Planner가 사용한 가정과 계산 범위 |
| `planningAssumptions.timeZone` | string | O | 일정 시간대 |
| `planningAssumptions.lodgingMode` | string | O | 숙소 모드 |
| `planningAssumptions.routeCoverage` | string | O | 경로 평가 범위 |
| `planningAssumptions.warnings` | array | O | 가정·실제 경로 보정에 따른 경고 코드. 시간 적합성을 위해 선택 방문지를 줄이면 `OPTIONAL_STOPS_REDUCED_FOR_FEASIBILITY` 포함 |
| `evaluation.qualityScore.evaluationCoveragePercent` | integer | O | 실제 평가한 품질 항목 비율(0~100) |
| `evaluation.qualityScore.metrics[].status` | string | O | `EVALUATED`, `NOT_EVALUATED` |

V2 응답은 top-level `dailyStartTime`, `dailyEndTime`을 사용하지 않는다. 일차별 실제 시간은 `days[].startTime`, `days[].endTime`이 기준이다. 숙소 미정이면 `days[].startLocation`, `days[].endLocation`, 지도 `startMarker`, `endMarker`가 `null`일 수 있다.

숙소 미정으로 평가하지 않은 항목은 점수 `0`으로 처리하지 않는다. `totalScore`는 `EVALUATED` 항목만 100점 기준으로 정규화하며, 서로 다른 `evaluationCoveragePercent`의 일정 점수를 직접 비교하지 않는다.

### V2-7. 일정 단건 조회와 목록

`GET /api/v1/schedules/{scheduleId}`는 저장된 `ScheduleResponse`를 반환한다. 생성 순간에만 계산하고 저장하지 않은 `evaluation` 운영 지표는 생략하며 `planningAssumptions`는 반환한다.

`GET /api/v1/schedules`의 `items`는 `startDate ASC`, 같은 시작일은 `createdAt DESC` 순서다. V2에서도 인증 도입 전까지 전체 일정을 반환한다.

## 3. 규칙 기반 다일 일정 생성 (V1 현재 구현)

`POST /api/v1/schedules`

| 요청 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `startDate` | string(date) | O | 여행 시작일 |
| `endDate` | string(date) | O | 여행 종료일. 시작일 포함 최대 4일 |
| `dailyStartTime` | string(time) | O | 매일 일정 시작시각 |
| `dailyEndTime` | string(time) | O | 매일 일정 종료시각 |
| `startLocation` | object | O | 첫날 출발 위치 |
| `startLocation.name` | string | O | 출발지명 |
| `startLocation.longitude` | number | O | 출발지 경도 |
| `startLocation.latitude` | number | O | 출발지 위도 |
| `endLocation` | object | O | 마지막 도착 위치 |
| `endLocation.name` | string | O | 도착지명 |
| `endLocation.longitude` | number | O | 도착지 경도 |
| `endLocation.latitude` | number | O | 도착지 위도 |
| `selectedAnswers` | array | O | 선택한 질문과 답변 목록 |
| `selectedAnswers[].questionId` | string | O | `questions.id` |
| `selectedAnswers[].answerId` | string | O | `answers.id` |
| `mustVisitPlaceIds` | array | X | 중복 없는 필수 방문 장소 ID 목록. 여행 일수당 최대 5개 |
| `mustVisitPlaceIds[]` | integer | O | 내부 `places.id` 한 개 |
| `days` | array | X | 일차별 출발·도착·시간 조건. 전달 시 전체 일차 필수 |
| `days[].dayNo` | integer | O | 1부터 시작하는 여행 일차. 중복 불가 |
| `days[].startTime` | string(time) | O | 해당 일차 일정 시작시각 |
| `days[].endTime` | string(time) | O | 해당 일차 일정 종료시각 |
| `days[].startLocation` | object | O | 해당 일차 출발 위치 |
| `days[].startLocation.name` | string | O | 일차 출발지명 |
| `days[].startLocation.longitude` | number | O | 일차 출발지 경도 |
| `days[].startLocation.latitude` | number | O | 일차 출발지 위도 |
| `days[].endLocation` | object | O | 해당 일차 도착 위치 |
| `days[].endLocation.name` | string | O | 일차 도착지명 |
| `days[].endLocation.longitude` | number | O | 일차 도착지 경도 |
| `days[].endLocation.latitude` | number | O | 일차 도착지 위도 |
| `days[].startLocationSource` | string/null | X | `USER`, `LODGING`, `DAY_OVERRIDE`, `PLANNER_DECIDES`, `LEGACY` |
| `days[].endLocationSource` | string/null | X | `LODGING`, `END_CONSTRAINT`, `DAY_OVERRIDE`, `LAST_STOP`, `LEGACY` |

`selectedAnswers`는 활성 상태인 모든 필수 질문을 포함해야 하며 질문당 답변 하나만 허용한다. `answerId`는 같은 항목의 `questionId`에 속한 활성 답변이어야 한다.

| 응답 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `id` | string(UUID) | O | 일정 ID |
| `status` | string | O | 일정 상태 |
| `startDate` | string(date) | O | 여행 시작일 |
| `endDate` | string(date) | O | 여행 종료일 |
| `dailyStartTime` | string(time) | O | 기본 일일 시작시각 |
| `dailyEndTime` | string(time) | O | 기본 일일 종료시각 |
| `styleSummary` | string | X | 답변 기반 여행 스타일 요약 |
| `days` | array | O | 날짜별 일정 |
| `days[].dayNo` | integer | O | 여행 일차 |
| `days[].date` | string(date) | O | 실제 날짜 |
| `days[].startTime` | string(time) | O | 해당 일차 시작시각 |
| `days[].endTime` | string(time) | O | 해당 일차 종료 목표시각 |
| `days[].startLocation` | object | O | 해당 일차 실제 출발 위치 |
| `days[].startLocation.name` | string | O | 일차 출발지명 |
| `days[].startLocation.longitude` | number | O | 일차 출발지 경도 |
| `days[].startLocation.latitude` | number | O | 일차 출발지 위도 |
| `days[].endLocation` | object | O | 해당 일차 실제 도착 위치 |
| `days[].endLocation.name` | string | O | 일차 도착지명 |
| `days[].endLocation.longitude` | number | O | 일차 도착지 경도 |
| `days[].endLocation.latitude` | number | O | 일차 도착지 위도 |
| `days[].summary` | string | X | 하루 일정 요약 |
| `days[].stops` | array | O | 해당 날짜의 방문 계획 |
| `days[].stops[].id` | string(UUID) | O | 방문 계획 ID |
| `days[].stops[].order` | integer | O | 날짜 안에서 방문 순서 |
| `days[].stops[].arriveAt` | string(time) | O | 장소 도착 예정시각 |
| `days[].stops[].departAt` | string(time) | O | 장소 출발 예정시각 |
| `days[].stops[].stayMinutes` | integer | O | 체류시간(분) |
| `days[].stops[].place` | object | O | 방문 장소 |
| `days[].stops[].place.id` | integer | O | 내부 장소 ID |
| `days[].stops[].place.name` | string | O | 장소명 |
| `days[].stops[].place.category` | string | X | 표시용 분류 |
| `days[].stops[].place.categoryLabel` | string | O | 사용자 표시용 분류 |
| `days[].stops[].mealTimeSlot` | string/null | X | 자동 식사 추천 창. `LUNCH`, `DINNER` |
| `days[].stops[].waitingMinutesBefore` | integer | O | 식사·고정 시간창 정렬로 장소 도착 전에 대기하는 시간(분) |
| `days[].stops[].place.address` | string | X | 주소 |
| `days[].stops[].place.longitude` | number | O | 장소 경도 |
| `days[].stops[].place.latitude` | number | O | 장소 위도 |
| `days[].stops[].place.primaryImageUrl` | string/null | X | 대표 이미지 URL |
| `days[].stops[].place.operatingInfo` | object/null | X | 운영정보 |
| `days[].stops[].place.operatingInfo.openingHoursText` | string/null | X | 운영시간 원문 |
| `days[].stops[].place.operatingInfo.closedDaysText` | string/null | X | 휴무일 원문 |
| `days[].stops[].place.operatingInfo.requiresManualCheck` | boolean | O | 원문 직접 확인 필요 여부 |
| `days[].stops[].inboundTransit` | object | X | 이전 지점에서 현재 장소로 들어오는 경로 |
| `days[].stops[].inboundTransit.routeType` | string | O | `INBOUND` |
| `days[].stops[].inboundTransit.routeOrder` | integer | O | 날짜 안 경로 순서 |
| `days[].stops[].inboundTransit.originName` | string | O | 경로 출발 지점명 |
| `days[].stops[].inboundTransit.destinationName` | string | O | 경로 도착 지점명 |
| `days[].stops[].inboundTransit.summary` | string | O | 사용자용 이동 요약 |
| `days[].stops[].inboundTransit.departAt` | string(time) | O | 이동 시작 예정시각 |
| `days[].stops[].inboundTransit.arriveAt` | string(time) | O | 이동 종료 예정시각 |
| `days[].stops[].inboundTransit.totalMinutes` | integer | O | 전체 이동시간(분) |
| `days[].stops[].inboundTransit.walkMinutes` | integer | O | 도보 이동 합계(분) |
| `days[].stops[].inboundTransit.waitMinutes` | integer | O | 대기시간 합계(분). 실시간 미연동 시 `0` |
| `days[].stops[].inboundTransit.transferCount` | integer | O | 환승 횟수 |
| `days[].stops[].inboundTransit.fareAmount` | integer | X | 예상 요금(원) |
| `days[].stops[].inboundTransit.provider` | string | O | `ODSAY`, `INTERNAL_WALK`, `FAKE`, `UNKNOWN` 등 |
| `days[].stops[].inboundTransit.realtimeStatus` | string | O | `AVAILABLE`, `PARTIAL`, `UNAVAILABLE` |
| `days[].stops[].inboundTransit.fallbackUsed` | boolean | O | fallback 경로 사용 여부 |
| `days[].stops[].inboundTransit.segments` | array | O | 교통 구간 목록 |
| `days[].stops[].inboundTransit.warnings` | array | O | 경로 주의사항 |
| `days[].stops[].selectionReasons` | array | O | 장소 선택 이유 |
| `days[].stops[].warnings` | array | O | 방문지 주의사항 |
| `segments[].order` | integer | O | 경로 안 구간 순서 |
| `segments[].mode` | string | O | `WALK`, `BUS`, `SUBWAY` |
| `segments[].lineName` | string | X | 버스 번호 또는 호선 |
| `segments[].startStationId` | string/null | X | 승차 정류장 또는 역 외부 ID |
| `segments[].startStationName` | string | X | 승차 정류장 또는 출발역 |
| `segments[].endStationId` | string/null | X | 하차 정류장 또는 역 외부 ID |
| `segments[].endStationName` | string | X | 하차 정류장 또는 도착역 |
| `segments[].instruction` | string | O | 사용자에게 보여줄 구간 안내문 |
| `segments[].durationMinutes` | integer | O | 구간 소요시간(분) |
| `segments[].distanceMeters` | integer/null | X | 구간 거리(m) |
| `segments[].stationCount` | integer/null | X | 경유 정류장 또는 역 수 |
| `segments[].waitMinutes` | integer | O | 해당 구간 탑승 전 대기시간 |
| `segments[].realtimeStatus` | string | O | 실시간 정보 반영 상태 |
| `days[].finalTransit` | object/null | X | 마지막 장소에서 최종 도착지까지 경로 |
| `days[].finalTransit.*` | - | O | `inboundTransit`과 같은 필드 구조. `routeType`은 `FINAL` |
| `evaluation` | object | O | 생성 시점 Planner 평가 보고서 |
| `evaluation.hardGate.passed` | boolean | O | 필수 장소·빈 일차·경로·시간 제약 통과 여부. 성공 응답은 `true` |
| `evaluation.hardGate.violations` | array | O | Hard Gate 위반 ID. 성공 응답은 빈 배열 |
| `evaluation.qualityScore.totalScore` | integer | O | 품질 점수 합계 |
| `evaluation.qualityScore.maxScore` | integer | O | 품질 점수 만점. 현재 `100` |
| `evaluation.qualityScore.evaluationCoveragePercent` | integer | O | 실제 평가한 지표 배점 비율 |
| `evaluation.qualityScore.unusedMinutes` | integer | O | 전체 일차에서 사용하지 않은 가용시간 합계(분) |
| `evaluation.qualityScore.longTransitWarnings` | array | O | 60분을 초과한 이동 구간 목록 |
| `evaluation.qualityScore.longTransitWarnings[].dayNo` | integer | O | 이동 구간 일차 |
| `evaluation.qualityScore.longTransitWarnings[].routeOrder` | integer | O | 일차 내 경로 순서 |
| `evaluation.qualityScore.longTransitWarnings[].originName` | string | O | 출발 지점명 |
| `evaluation.qualityScore.longTransitWarnings[].destinationName` | string | O | 도착 지점명 |
| `evaluation.qualityScore.longTransitWarnings[].totalMinutes` | integer | O | 이동시간(분) |
| `evaluation.qualityScore.routeConfidence` | string | O | `HIGH`, `MEDIUM`, `LOW`, `UNKNOWN` |
| `evaluation.qualityScore.metrics` | array | O | 품질 지표별 평가 결과 |
| `evaluation.qualityScore.metrics[].id` | string | O | `TIME_FIT`, `MOBILITY_FIT`, `TRANSIT_FIT`, `PREFERENCE_FIT`, `ENDPOINT_FIT` |
| `evaluation.qualityScore.metrics[].label` | string | O | 지표 표시명 |
| `evaluation.qualityScore.metrics[].score` | integer | O | 지표 획득 점수 |
| `evaluation.qualityScore.metrics[].maxScore` | integer | O | 지표 배점 |
| `evaluation.qualityScore.metrics[].reason` | string | O | 점수 산정 근거 |
| `evaluation.operations.generationMillis` | integer | O | 검증부터 저장·평가까지 생성 소요시간(ms) |
| `evaluation.operations.planningMode` | string | O | `AI_GENERATED`, `AI_ASSISTED`, `AI_FALLBACK`, `RULE_BASED` |
| `evaluation.operations.aiPlanConfidence` | integer/null | X | 검증을 통과한 AI 장소·날짜 제안 신뢰도 `0~100` |
| `evaluation.operations.multiDayPlanCandidateCount` | integer | O | AI 제안을 포함해 보존한 다일 장소·날짜 배치안 수 |
| `evaluation.operations.multiDayPlanRerankedCount` | integer | O | 실제 경량 대중교통 경로로 평가를 완료한 다일 배치안 수 |
| `evaluation.operations.routeEstimateResolutionCount` | integer | O | 실제 경로 재평가 후보의 경량 경로 해석 요청 횟수 |
| `evaluation.operations.routeEstimateCacheHitCount` | integer | O | 경량 경로 요청 단위 캐시 적중 횟수 |
| `evaluation.operations.providerEstimateCallCount` | integer | O | 실제 경로 재평가를 위한 Provider 경량 조회 횟수 |
| `evaluation.operations.providerEstimateFailureCount` | integer | O | Provider 경량 조회 실패 횟수 |
| `evaluation.operations.routeResolutionCount` | integer | O | 최종 선택 구간의 실제 경로 해석 요청 횟수 |
| `evaluation.operations.routeCacheHitCount` | integer | O | 최종 선택 구간의 요청 단위 실제 경로 캐시 적중 횟수 |
| `evaluation.operations.providerCallCount` | integer | O | 최종 선택 구간을 확정하기 위한 `TransitRouteProvider` 호출 횟수 |
| `evaluation.operations.providerFailureCount` | integer | O | 외부 Provider 호출 실패 횟수 |
| `evaluation.operations.externalHttpCallCount` | integer | O | ODsay 경로검색·loadLane·TMAP 도보 HTTP 시도 합계 |
| `evaluation.operations.externalHttpFailureCount` | integer | O | 외부 HTTP 오류·유효하지 않은 응답 횟수 |
| `evaluation.operations.odsayPathSearchCount` | integer | O | ODsay 대중교통 경로검색 요청 수 |
| `evaluation.operations.odsayLoadLaneCount` | integer | O | ODsay 상세 선형 loadLane 요청 수 |
| `evaluation.operations.tmapWalkingCount` | integer | O | TMAP 보행자 경로 요청 수 |
| `evaluation.operations.routeCount` | integer | O | 최종 일정에 포함된 이동 경로 수 |
| `evaluation.operations.fallbackRouteCount` | integer | O | 최종 일정의 fallback 경로 수 |
| `evaluation.operations.geometryFallbackLineCount` | integer | O | 상세 선형 대신 단순 좌표를 사용한 지도 경로선 수 |
| `evaluation.operations.totalTransitMinutes` | integer | O | 최종 일정 전체 이동시간(분) |
| `evaluation.operations.totalWalkMinutes` | integer | O | 최종 일정 전체 도보시간(분) |
| `evaluation.operations.totalTransferCount` | integer | O | 최종 일정 전체 환승 횟수 |
| `evaluation.operations.providers` | array | O | 최종 일정 경로 Provider 목록 |

## 4. 일정 목록 조회

`GET /api/v1/schedules`

요청값은 없으며 전체 일정 목록을 반환한다. 응답의 일정 객체는 일정 생성 응답과 같은 의미를 가지며 생성 시점의 `evaluation`은 생략한다.

| 응답 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `items` | array | O | 저장된 일정 목록 |
| `items[].id` | string(UUID) | O | 일정 ID |
| `items[].status` | string | O | 일정 상태 |
| `items[].startDate` | string(date) | O | 시작일 |
| `items[].endDate` | string(date) | O | 종료일 |
| `items[].styleSummary` | string | X | 여행 스타일 요약 |
| `items[].days` | array | O | 날짜별 일정 |

## 5. 일정 수정

`PATCH /api/v1/schedules/{scheduleId}`

| 요청 필드 | 위치 | 자료형 | 필수 | 의미 |
| --- | --- | --- | :---: | --- |
| `scheduleId` | Path | string(UUID) | O | 수정할 일정 |
| `stops` | Body | array | O | 수정 후 전체 방문 계획 |
| `stops[].stopId` | Body | string(UUID) | 조건부 | 유지 또는 수정할 기존 방문 계획 |
| `stops[].placeId` | Body | integer | 조건부 | 새로 추가할 내부 장소 |
| `stops[].dayNo` | Body | integer | O | 배치할 여행 일차 |
| `stops[].order` | Body | integer | O | 해당 일차의 방문 순서 |
| `stops[].stayMinutes` | Body | integer | O | 체류시간(분). 최소 `30` |

`stopId`와 `placeId`는 하나만 전달한다. 모든 여행 일차에 방문 장소가 1~3개 있어야 하며 일차별 `order`는 `1`부터 중복과 누락 없이 연속되어야 한다. `version`은 사용하지 않는다.

| 응답 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `id` | string(UUID) | O | 수정된 일정 ID |
| `status` | string | O | 일정 상태 |
| `days` | array | O | 수정된 날짜별 일정 |
| `days[].dayNo` | integer | O | 여행 일차 |
| `days[].stops` | array | O | 수정된 방문 계획 |
| `days[].stops[].id` | string(UUID) | O | 방문 계획 ID |
| `days[].stops[].order` | integer | O | 방문 순서 |
| `days[].stops[].stayMinutes` | integer | O | 체류시간 |

## 6. 장소 검색

`GET /api/v1/places`

| 요청 필드 | 위치 | 자료형 | 필수 | 의미 |
| --- | --- | --- | :---: | --- |
| `keyword` | Query | string | 조건부 | 이름 기반 내부 DB 검색어 |
| `longitude` | Query | number | 조건부 | 위치 검색 중심 경도 |
| `latitude` | Query | number | 조건부 | 위치 검색 중심 위도 |
| `radius` | Query | integer | X | 검색 반경(m) |

| 응답 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `items` | array | O | 장소 결과 |
| `items[].id` | integer | O | 내부 장소 ID |
| `items[].externalContentId` | string | O | TourAPI 콘텐츠 ID |
| `items[].name` | string | O | 장소명 |
| `items[].category` | string | X | 표시용 분류 |
| `items[].address` | string | X | 주소 |
| `items[].longitude` | number | O | 경도 |
| `items[].latitude` | number | O | 위도 |
| `items[].distanceMeters` | integer/null | X | 검색 중심과 거리 |
| `items[].primaryImageUrl` | string/null | X | 대표 이미지 |

## 7. 장소 상세

`GET /api/v1/places/{placeId}`

| 응답 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `id` | integer | O | 내부 장소 ID |
| `externalContentId` | string | O | TourAPI 콘텐츠 ID |
| `contentTypeId` | string | X | TourAPI 관광 타입 |
| `name` | string | O | 장소명 |
| `address` | string | X | 주소 |
| `longitude` | number | O | 경도 |
| `latitude` | number | O | 위도 |
| `overview` | string | X | 상세 설명 |
| `operatingInfo` | object | X | 운영 정보 |
| `operatingInfo.openingHoursText` | string | X | 운영시간 원문 |
| `operatingInfo.closedDaysText` | string | X | 휴무일 원문 |
| `operatingInfo.useFeeText` | string | X | 이용요금 원문 |
| `operatingInfo.parkingText` | string | X | 주차 원문 |
| `operatingInfo.requiresManualCheck` | boolean | O | 원문 직접 확인 필요 여부 |
| `images` | array | O | 이미지 목록 |
| `images[].url` | string | O | 원본 이미지 |
| `images[].thumbnailUrl` | string | X | 썸네일 |
| `images[].copyrightType` | string | X | 저작권 구분 |

## 8. 주변 편의시설

`GET /api/v1/places/{placeId}/nearby-facilities`

| 요청 필드 | 위치 | 자료형 | 필수 | 의미 |
| --- | --- | --- | :---: | --- |
| `placeId` | Path | integer | O | 검색 기준 장소 |
| `types` | Query | string | O | 1차 지원값 `CONVENIENCE_STORE` |
| `radius` | Query | integer | X | 검색 반경(m) |

| 응답 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `items` | array | O | 주변 편의점 목록 |
| `items[].externalId` | string | O | 카카오 장소 ID |
| `items[].type` | string | O | `CONVENIENCE_STORE` |
| `items[].name` | string | O | 시설명 |
| `items[].address` | string | X | 주소 |
| `items[].longitude` | number | O | 경도 |
| `items[].latitude` | number | O | 위도 |
| `items[].distanceMeters` | integer | X | 기준 장소와 거리 |
| `items[].placeUrl` | string | X | 카카오 장소 URL |
| `items[].source` | string | O | `KAKAO_LOCAL` |

## 9. 일정 지도

`GET /api/v1/schedules/{scheduleId}/map`

| 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `scheduleId` | string(UUID) | O | 일정 ID |
| `dayNo` | integer | X | 조회할 일차 |
| `startMarker` | object | O | 출발지 마커 |
| `endMarker` | object | O | 도착지 마커 |
| `markers` | array | O | 방문 장소 마커 |
| `markers[].dayNo` | integer | O | 여행 일차 |
| `markers[].order` | integer | O | 방문 순서 |
| `markers[].placeId` | integer | O | 장소 ID |
| `markers[].name` | string | O | 장소명 |
| `markers[].arriveAt` | string(time) | O | 장소 도착 예정시각 |
| `markers[].departAt` | string(time) | O | 장소 출발 예정시각 |
| `markers[].subtitle` | string | X | 카테고리와 체류시간 등 마커 보조 문구 |
| `markers[].riskLevel` | string | O | `NORMAL`, `NOTICE`, `WARNING` |
| `markers[].longitude` | number | O | 경도 |
| `markers[].latitude` | number | O | 위도 |
| `routeLines` | array | O | 지도 경로선 |
| `routeLines[].dayNo` | integer | O | 경로 일차 |
| `routeLines[].routeOrder` | integer | O | 날짜 내 경로 순서 |
| `routeLines[].lineOrder` | integer | O | 선 조각 순서 |
| `routeLines[].mode` | string | O | 이동수단. `WALK`, `BUS`, `SUBWAY` |
| `routeLines[].lineName` | string | X | 노선명 |
| `routeLines[].startName` | string | O | 선 조각 출발 지점명. 대중교통은 승차 정류장·역, 도보는 출발지·승하차 지점 |
| `routeLines[].endName` | string | O | 선 조각 도착 지점명. 대중교통은 하차 정류장·역, 도보는 승하차 지점·목적지 |
| `routeLines[].durationMinutes` | integer | X | 선 조각 소요시간 |
| `routeLines[].distanceMeters` | integer | O | 선 조각 거리(m). Provider 누락 시 polyline 좌표로 계산 |
| `routeLines[].instruction` | string | X | 지도에서 보여줄 선 조각 안내문 |
| `routeLines[].fallbackUsed` | boolean | O | fallback 좌표 사용 여부 |
| `routeLines[].coordinates` | array | O | `[경도, 위도]` 좌표 목록. 500m 초과 `WALK`는 TMAP을 우선 사용하고, 500m 이하 연결 도보 또는 Provider 실패 시 fallback 좌표를 사용 |

결과 화면의 일차별 `약 Nkm`는 해당 일차 `routeLines[].distanceMeters`의 null이 아닌 값을 합산해 계산한다. 경로선이 없을 때만 장소 좌표 간 직선거리를 임시 fallback으로 사용할 수 있다.

## 10. 공유 링크 생성

| 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `scheduleId` | string(UUID) | O | 공유할 일정 |
| `expiresInDays` | integer | X | 유효기간(일). `1~365` |
| `id` | string(UUID) | O | 공유 링크 관리 ID |
| `token` | string | O | 공유 URL 토큰 |
| `url` | string | O | 공유 상대 URL |
| `expiresAt` | string(datetime) | X | `Asia/Seoul` 오프셋을 포함한 ISO-8601 만료시각 |

## 11. 공유 일정·지도 조회

| 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `token` | string | O | 공유 링크 토큰 |
| `id` | string(UUID) | O | 공유 대상 일정 |
| `status` | string | O | 일정 상태 |
| `readOnly` | boolean | O | 항상 `true` |
| `days` | array | O | 날짜별 일정 |

공유 지도 응답 필드는 일반 일정 지도 응답과 동일하다.
만료되거나 폐기된 토큰은 `404 SHARE_LINK_NOT_FOUND`를 반환한다.

## 12. 공유 링크 폐기

`DELETE /api/v1/schedules/{scheduleId}/shares/{shareId}`

| 요청 필드 | 위치 | 자료형 | 필수 | 의미 |
| --- | --- | --- | :---: | --- |
| `scheduleId` | Path | string(UUID) | O | 일정 ID |
| `shareId` | Path | string(UUID) | O | 폐기할 공유 링크 ID |

응답 본문 없이 `204 No Content`를 반환한다.
