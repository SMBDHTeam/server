# 일정 생성 V2 기능 명세

> 상태: 구현 완료
>
> 이 문서는 일정 생성 V2의 제품 동작과 서버 책임을 정의한다. 신규 클라이언트는 `docs/API_SPEC.md`의 V2 계약을 사용하며 V1은 호환용으로 유지한다.

> 현재 실행기는 AI가 제한된 후보에서 일차별 장소 구성을 제안하고, 결정론적 Planner가 실제 경로·시간·Hard Gate를 검증하는 하이브리드 구조다. AI 전환 상태와 책임 경계는 `docs/ai-planner-roadmap.md`를 따른다.

## 1. 목표

사용자가 숙소와 세부 이동 조건을 모두 확정하지 않았어도 부산 당일·다일 일정을 먼저 만들어 볼 수 있어야 한다. 동시에 항공편, 기차편, 고정 행사처럼 반드시 지켜야 하는 조건은 Planner가 임의로 무시하지 않아야 한다.

일정 생성 V2는 다음 책임을 분리한다.

```text
사용자 입력
→ Preview에서 입력 정규화·기본값·충돌 확인
→ 사용자 확인
→ Planner가 확정된 Preview 실행
→ 일정 저장과 상세 응답
```

## 2. 핵심 원칙

1. 당일과 다일 일정은 같은 생성 흐름과 API를 사용한다.
2. 사용자는 최소 입력만 제공하고 서버가 일차별 실행 조건을 계산한다.
3. Preview는 생성 후 불변이며, 수정하면 새로운 Preview를 만든다.
4. Planner는 클라이언트가 다시 조립한 조건이 아니라 저장된 Preview를 실행한다.
5. 숙소 미정은 오류가 아니라 정상적인 탐색 모드다.
6. 구조화된 필수 조건과 자유 프롬프트를 분리한다.
7. Preview에서는 외부 경로 API를 반복 호출하지 않는다.
8. 외부 검색 장소는 사용자가 선택한 경우에만 내부 장소로 확정한다.

## 3. 지원 사용자 시나리오

### 3.1 부산 방문 전 탐색

- 사용자는 부산에 있지 않다.
- 시작일, 종료일, 부산에서의 시작 위치만 선택한다.
- 숙소와 마지막 도착지는 정하지 않을 수 있다.
- Planner는 방문지 중심의 초안 동선을 만들고 숙소 이동 미반영 범위를 경고한다.

### 3.2 부산 도착 후 당일 생성

- 시작일과 종료일이 오늘로 같다.
- 시작 위치는 사용자가 검색해 선택한 장소다.
- 시작시각을 생략하면 `Asia/Seoul` 현재시각을 다음 30분 단위로 올려 적용한다.
- 남은 시간이 최소 일정 가능 시간보다 짧으면 Preview에서 조정이 필요하다고 반환한다.

### 3.3 숙소가 정해진 다일 일정

- 같은 숙소를 사용하는 경우 `FIXED_BASE`를 사용한다.
- 날짜마다 숙소가 다른 경우 `PER_NIGHT`를 사용한다.
- 각 일차는 전날 숙소와 다음 숙소를 기준으로 연결한다.

### 3.4 마지막 교통편이 있는 일정

- 사용자는 공항·역·터미널과 출발시각을 입력한다.
- 서버는 이동수단 기본 여유시간 또는 사용자 지정 여유시간을 적용한다.
- 마지막 방문 장소에서 제한 시각까지 이동할 수 없는 일정은 생성하지 않는다.

### 3.5 행사·공연이 있는 일정

- 행사명, 내부 장소 ID, 시작·종료 날짜시간을 구조화해 입력한다.
- Planner는 행사를 고정 시간 슬롯으로 먼저 배치한다.
- 자유 프롬프트에만 작성된 행사는 Hard Constraint로 취급하지 않는다.

## 4. 사용자 입력 모델

### 4.0 프론트 기본 흐름

```text
/date → /step1 → /step2 → /step3 → /places → /preview → /generating → /trips/{scheduleId}
```

- `/date`에서 날짜와 시작 위치를 함께 받으며 위치 검색은 지도·검색 모달로 연다.
- GPS 현재 위치는 사용하지 않는다.
- 숙소, 종료 제약, 고정 행사, 일차별 조정, 자유 요청은 서버 계약은 유지하지만 프론트 1차 기본 흐름에서는 입력하지 않는다.
- 기본값은 `startTime=null`, `lodgingPlan.mode=UNDECIDED`, `endConstraint=null`, `fixedEvents=[]`, `dayOverrides=[]`, `customPrompt=null`이다. nullable 선택 필드는 JSON에서 생략해도 같은 의미다.

### 4.1 필수 입력

| 입력 | 의미 |
| --- | --- |
| `startDate` | 여행 시작일 |
| `endDate` | 여행 종료일. 시작일 포함 최대 4일 |
| `startLocation` | 첫날 여행을 시작할 위치 |
| `selectedAnswers` | 질문 단계 완료 후 필요한 활성 필수 선호도 질문의 답변 |

`startTime`은 선택값이다.

- 시작일이 오늘이면 KST 현재시각을 다음 30분 단위로 올린다.
- 시작일이 미래면 `10:00`을 적용한다.
- 적용한 기본값은 Preview의 `appliedDefaults`에 반환한다.

### 4.2 선택 입력

| 입력 | 의미 |
| --- | --- |
| `lodgingPlan` | 숙소 미정, 고정 거점, 날짜별 숙소 설정 |
| `endConstraint` | 마지막 도착 또는 항공·기차 출발 제약 |
| `dayOverrides` | 특정 날짜의 가용시간과 위치 변경 |
| `mustVisitPlaceIds` | 반드시 포함할 내부 장소 ID |
| `fixedEvents` | 반드시 배치할 행사·공연 |
| `customPrompt` | 구조화하기 어려운 소프트 선호 요청 |

## 5. 숙소 계획

### 5.1 `UNDECIDED`

숙소가 정해지지 않은 기본 모드다.

- 첫날 출발 위치는 사용자 입력을 사용한다.
- 마지막 날 종료 제약이 있으면 해당 위치를 사용한다.
- 중간 일차의 숙소 출발·도착 경로는 계산하지 않는다.
- Planner는 방문지를 지역별로 묶고 전날 마지막 장소와 다음 날 첫 장소의 거리를 줄인다.
- Hard Gate와 품질 점수는 실제 계산한 경로 범위만 평가한다.
- 결과에 `routeCoverage: ATTRACTION_ROUTES_ONLY`와 `LODGING_ROUTE_EXCLUDED` 경고를 포함한다.

출발 위치를 모든 날의 거점으로 자동 사용하지 않는다.

### 5.2 `FIXED_BASE`

- 다일 일정에서 `baseLocation`이 필수다.
- 첫날은 `startLocation → baseLocation`으로 구성한다.
- 중간 일차는 `baseLocation → baseLocation`으로 구성한다.
- 마지막 날 `endConstraint`가 있으면 `baseLocation → endConstraint.location`, 없으면 거점으로 돌아온다.

### 5.3 `PER_NIGHT`

- 여행의 각 숙박일에 `nightStays[].date`와 위치를 전달한다.
- 숙박일은 `startDate`부터 `endDate` 전날까지 중복과 누락 없이 포함해야 한다.
- 다음 날 기본 출발지는 전날 숙소다.

## 6. 일차별 시간 계산

Preview는 다음 순서로 일차별 `availableFrom`, `availableUntil`을 계산한다.

1. 첫날 `availableFrom`: 사용자 `startTime` 또는 적용된 기본값
2. 중간·마지막 날 `availableFrom`: 기본 `10:00`
3. 모든 일차 `availableUntil`: 기본 `20:00`
4. 마지막 날 종료 제약이 있으면 `targetAt - appliedBufferMinutes`
5. `dayOverrides`가 있으면 해당 날짜의 필드를 덮어쓴다.
6. 고정 행사와 최소 이동 가능 시간을 검증한다.

`dayOverrides`는 `dayNo`가 아니라 실제 `date`를 식별자로 사용한다. 날짜 범위가 바뀌면 범위를 벗어난 override는 자동 이동하지 않고 검증 오류로 처리한다.
종료 제약이 있는 마지막 날은 `dayOverrides.endLocation`으로 목적지를 바꿀 수 없고, 가용 종료시각도 종료 제약의 제한시각을 넘지 못한다.

## 7. 종료 제약

| 타입 | 의미 | 기본 여유시간 |
| --- | --- | ---: |
| `ARRIVE_BY` | 해당 시각까지 위치에 도착 | 0분 |
| `TRAIN_DEPARTURE` | 기차 출발 | 30분 |
| `FLIGHT_DEPARTURE` | 항공편 출발 | 90분 |

사용자는 `bufferMinutes`를 직접 지정할 수 있다. 서버는 실제 적용값을 Preview의 `appliedBufferMinutes`로 반환한다. 국제선 등 더 긴 시간이 필요한 경우 프론트가 권장값을 제시하고 사용자가 확인하도록 한다.

## 8. 선호도와 제약 우선순위

Planner는 다음 우선순위를 고정한다.

```text
fixedEvents / endConstraint
→ mustVisitPlaceIds
→ 운영시간·휴무·이동 가능성
→ 점심·저녁 자동 추천 시간창
→ selectedAnswers
→ interpretedPrompt
```

### 8.1 `selectedAnswers`

- 필수 `SINGLE_CHOICE` 질문은 `answerIds`가 정확히 1개여야 하고 선택 질문은 생략할 수 있다.
- `MULTIPLE_CHOICE` 질문은 질문별 `minSelections`, `maxSelections` 범위여야 한다.
- 질문·답변 소속 관계와 활성 상태를 서버가 검증한다.
- 질문 응답의 `uiStep`으로 기본 화면 Step1~3을 그룹화한다.
- `COMPANION`, `MOBILITY`, `PACE`, `TRANSIT`, `THEME`와 활성 답변 ID는 저장 요청에 사용하는 안정적인 계약이다.

### 8.2 `customPrompt`

- 최대 500자다.
- Hard Constraint로 사용하지 않는다.
- Preview에서 `interpretedPrompt.preferences`와 `unrecognizedTexts`로 정규화한다.
- Planner는 정규화된 선호를 점수에 반영하고, 원문은 AI 일정 제안에 소프트 조건으로 전달한다.
- 해석하지 못한 문장은 무시하지 않고 사용자에게 표시한다.
- 현재 지원하는 정규화 값은 `LOW_WALKING`, `PREFER_SEA_VIEW`, `PREFER_FOOD`다.
- `AI_PLANNER_ENABLED=false`이면 `RuleBasedPlanningPromptInterpreter`의 키워드 규칙을 사용한다.
- AI가 활성화되면 Structured Outputs로 허용된 선호 코드만 해석하고, 키 누락·시간 초과·Provider 오류·출력 검증 실패 시 규칙 기반 결과로 fallback한다.
- 응답의 `source`는 `RULE_BASED`, `HYBRID_AI`, `FALLBACK`이고 `confidence`는 `0~100`이다.

### 8.3 AI 일정 제안

- AI가 활성화되고 API 키가 있으면 후보 장소 ID, 일차별 가용시간·출발·도착, 질문 답변, 필수 장소, 고정 행사, 식사 슬롯과 `customPrompt`를 Structured Outputs 입력으로 사용한다.
- AI는 서버가 전달한 후보 ID만 선택하며 각 일차의 목표 방문 수를 변경할 수 없다.
- 서버는 날짜 수, 중복, 후보 소속, 필수 장소, 고정 행사 날짜, 일차별 식사 슬롯을 다시 검증한다.
- 검증된 AI 장소·날짜 배치안은 규칙 기반 Top-K와 함께 실제 경로로 비교한다. 방문 순서와 경로, 시간 feasibility, Hard Gate는 결정론적 Planner가 확정한다.
- 일정 응답의 `evaluation.operations.planningMode`는 `AI_GENERATED`, `AI_ASSISTED`, `AI_FALLBACK`, `RULE_BASED` 중 하나다.
- AI 호출은 규칙 기반 후보 탐색과 병렬 실행하며, 실패하거나 8초 타임아웃을 넘으면 규칙 후보로 계속 생성한다.

## 9. 장소 검색과 확정

1. `GET /places`가 내부 장소를 먼저 검색한다.
2. `scope=ALL`이면 결과가 부족할 때 Kakao Local 결과를 보강한다.
3. 외부 결과는 `placeId: null`, `source`, `externalId`를 반환한다.
4. 사용자가 외부 결과를 선택하면 `POST /places/resolve`를 호출한다.
5. 서버는 `(source, external_content_id)` 중복을 확인하고 선택한 장소만 upsert한다.
6. Preview와 Planner에는 내부 `placeId`만 전달한다.

Kakao 장소는 운영시간·이미지·TourAPI 상세정보가 없을 수 있다. 이 경우 일정 결과에 수동 확인 경고를 포함한다.

## 10. Preview 생명주기

```text
POST /schedule-previews
→ READY 또는 REQUIRES_ACTION
→ 사용자 확인
→ POST /schedules + Idempotency-Key
→ CONSUMED
```

- 유효시간은 생성 후 30분이다.
- Preview는 생성 후 수정하지 않는다.
- 새로고침 복원을 위해 `GET /schedule-previews/{previewId}`를 제공한다.
- 만료된 Preview는 `410 PREVIEW_EXPIRED`를 반환한다.
- 한 Preview에서는 일정 하나만 생성할 수 있다.
- 이미 소비된 Preview를 다른 멱등성 키로 실행하면 `409 PREVIEW_ALREADY_CONSUMED`를 반환한다.
- 같은 멱등성 키의 실행이 아직 끝나지 않으면 최대 5초 기다린 뒤 `409 SCHEDULE_CREATION_IN_PROGRESS`를 반환한다.

## 11. Preview 검증 범위

Preview는 다음 항목을 검증한다.

- 날짜 범위와 최대 4일
- 질문·답변 관계와 선택 수
- 숙소 모드별 필수값
- 날짜별 override 중복·범위
- 고정 행사 시간 중복과 여행 기간 포함 여부
- 종료 제약과 일차 가용시간
- 필수 방문 장소 수와 존재 여부
- 좌표 범위와 위치 필수값

Preview에서는 ODsay·TMAP 상세 경로를 호출하지 않는다. 직선거리 기반으로 명백한 충돌만 차단하고 불확실한 이동은 `POTENTIAL_TRANSIT_CONFLICT` warning으로 반환한다. 실제 대중교통 가능성은 Planner가 검증한다.

## 12. Planner 결과 정책

자동 추천은 목표 방문지 수보다 최대 6곳 큰 후보 풀을 구성한다. 이때 콘텐츠 타입뿐 아니라 장소명·카테고리에서 파생한 체험 유형과 의미 그룹이 다른 후보를 우선 보존한다. Planner는 최대 20개 후보 안에서 필수 장소를 보존하면서 장소 선택과 날짜 배치를 비트마스크 동적계획법으로 함께 비교한다. 다일 배치 비교는 `confirmedHardViolations → feasibilityRisk → fatigueCost → routeFlowCost → rhythmCost → preferenceCost → diversityCost → placeCountCost` 순의 사전식 `PlanObjective`를 사용한다. 이 단계에서 확정할 수 없는 실제 경로·시간 비용은 0으로 두며, 부분 상태의 현재 장소 수만으로 가지치기하지 않는다. 방문 순서가 아직 없으므로 `routeFlowCost`는 일차 출발·도착을 포함한 좌표 기반 최소 경로 추정이다. 동일 목적함수에서는 후보 적합도, 좌표 거리, 결정적 계획 키 순으로 비교한다. 동일 장소 집합의 날짜 배정이 다른 상태도 Top-K로 보존한다. 이후 다일 배치안은 일차 출발·도착 경계의 실제 대중교통 접근성으로 재평가하고, 선택된 배치안의 일차별 상위 방문 순서·식사 위치 후보는 요청당 외부 호출 예산 안에서 전체 실제 경로로 비교한 뒤 상세화한다.

선호 테마는 장소마다 동일 보너스를 무제한 합산하지 않는다. 하루의 첫 번째 일치 장소에는 전체 보상, 두 번째에는 50%, 세 번째에는 15%만 적용하고 이후 일치에는 추가 보상을 주지 않는다. 선호 장소가 전혀 없는 일정에는 미충족 비용을 부여하되, 같은 체험 유형과 의미 그룹의 반복에는 별도 비용을 부여한다. 다일 일정은 이전 날짜와 현재 날짜 사이의 동일 체험 반복도 감점한다. 실제 이동비가 비슷한 방문 순서에서는 같은 체험이 연속하지 않는 순서를 우선한다. 음식점과 카페는 식사 시간창 정책이 우선하므로 일반 체험 반복 감점에서 제외한다.

장소 의미는 단일 테마가 아니라 `장소 정체성`, `물리적 환경`, `가능한 경험`, `콘텐츠`, `분위기`의 다축 프로필로 계산한다. 예를 들어 해변 축제는 행사 콘텐츠이면서 해안 환경과 바다 조망 경험을 함께 가진다. 체험 중복은 주 콘텐츠 타입이 달라도 이 프로필의 환경·정체성·경험 기여도가 충분히 겹치면 감점한다. 고정 행사는 현재 연결된 `placeId`의 장소 의미를 사용한다. 행사 전후의 해변 산책처럼 별도 활동을 배정하고 저장하는 기능은 `ScheduleStop` 활동 계약과 함께 후속 API·ERD 변경으로 추가한다.

일차 방문 순서는 이동시간만 최소화하지 않는다. 서로 다른 권역으로의 첫 이동은 약하게 감점하지만, 이미 떠난 권역으로 다시 들어가는 이동은 강하게 감점한다. 1km 이상인 연속 이동 구간이 90도 이상 꺾이면 방향 전환 비용도 반영한다. 출발지와 도착지가 1km 이내인 마지막 복귀는 순환형 일정으로 보고 권역 재진입 예외를 적용한다. 고정 행사와 필수 장소, 시간 Hard Gate는 이 품질 비용보다 항상 우선한다. 좌표 기반 `detourRatio`는 순열 간 결과 지표로 계산하며, 실제 Provider 반복 측정으로 임계치를 정하기 전에는 감점에 사용하지 않는다.

### 12.1 숙소 미정

- `routeCoverage`는 `ATTRACTION_ROUTES_ONLY`다.
- 숙소 이동은 `NOT_EVALUATED`로 표시한다.
- 품질 지표에 `status=EVALUATED|NOT_EVALUATED`와 `evaluationCoveragePercent`를 반환한다.
- `NOT_EVALUATED` 항목을 0점으로 처리하지 않고 평가한 항목만 100점 기준으로 정규화한다.
- 평가 범위가 다른 일정의 총점을 직접 비교하지 않는다.
- 지도에는 실제 계산한 장소 간 경로만 표시한다.
- 숙소가 정해진 뒤 새 Preview로 다시 생성할 수 있다.

### 12.2 숙소 확정

- `routeCoverage`는 `FULL`이다.
- 일차별 출발·도착 경로를 Hard Gate에 포함한다.

### 12.3 고정 행사

- 생성된 `schedule_stop`과 일대일로 연결한다.
- 한 Preview에서 같은 `placeId`를 둘 이상의 고정 행사에 중복 사용하지 않는다.
- 행사 시작·종료 시각을 변경하지 않는다.
- 앞뒤 이동이 불가능하면 `422 FIXED_EVENT_UNREACHABLE`을 반환한다.

### 12.4 식사 시간 자동 추천

- 점심 창은 `11:00~14:00`, 저녁 창은 `17:00~19:00`이다.
- 일차 가용시간과 시간창이 45분 이상 겹칠 때만 해당 창을 활성화한다.
- TourAPI 음식점 타입과 음식점·카페 카테고리 또는 명칭을 식사 후보로 분류한다.
- 하루 전체 방문지는 식사·카페·고정 행사를 포함해 최대 5곳이다. 식사는 별도 추가 슬롯이 아니라 전체 목표 안에서 배치한다.
- 기본 속도는 8시간 이상일 때 4곳, 6시간 이상일 때 3곳을 목표로 한다. `PACE_RELAXED`는 가용시간 6시간 이상일 때 3곳, 4시간 이상일 때 2곳을 목표로 한다. `PACE_PACKED`는 8시간 이상일 때 5곳, 6시간 이상일 때 4곳을 목표로 한다. 3시간 미만은 1곳을 목표로 한다. 목표보다 적은 일정은 장시간 행사·필수 장소·실제 이동시간으로 feasibility가 우선될 때 허용한다.
- 야경은 저녁 이후, 시장은 오전 10시 이후, 박물관은 저녁 이전, 해변·공원·숲길은 일몰 전을 권장한다. 구조화된 운영시간이 없는 현재 적재 데이터에서는 이는 Hard Gate가 아닌 경로 순열의 Soft Penalty다.
- 식사 장소가 일찍 도착하면 창 시작 시각까지 대기한다. 대기는 종료시각 feasibility에는 포함하되 활동시간으로 계산하지 않아 품질표의 미사용 시간에 남긴다.
- 필수 장소와 고정 행사가 우선이고 후보 부족은 생성 실패 조건이 아니다.

## 13. 상태와 오류

### 13.1 Preview 상태

| 상태 | 의미 |
| --- | --- |
| `READY` | 확인 후 생성 가능 |
| `REQUIRES_ACTION` | 충돌 수정 필요 |
| `EXPIRED` | 유효시간 만료 |
| `CONSUMED` | 일정 생성에 사용됨 |

### 13.2 주요 오류·충돌 코드

사용자가 수정할 수 있는 Preview 충돌은 `201 Created`의 `conflicts[]`로 반환한다. Preview 자체를 만들 수 없는 형식·관계 오류만 HTTP 오류로 반환하며, 상세 경로 계산에서 새로 확인된 실행 불가능 조건은 Planner의 `422` 오류로 반환한다.

| 전달 위치 | HTTP | 코드 | 의미 |
| --- | ---: | --- | --- |
| HTTP 오류 | 400 | `INVALID_SCHEDULE_PREVIEW_REQUEST` | 필드 형식·관계 오류 |
| HTTP 오류 | 400 | `FIXED_BASE_LOCATION_REQUIRED` | 고정 거점 위치 누락 |
| HTTP 오류 | 400 | `PER_NIGHT_LOCATION_MISSING` | 날짜별 숙소 누락 |
| HTTP 오류 | 400 | `MUST_VISIT_PLACE_LIMIT_EXCEEDED` | 필수 장소 수 초과 |
| HTTP 오류 | 409 | `IDEMPOTENCY_KEY_REUSED` | 같은 키를 다른 요청에 사용 |
| HTTP 오류 | 409 | `PREVIEW_ALREADY_CONSUMED` | 다른 요청으로 이미 생성됨 |
| HTTP 오류 | 409 | `SCHEDULE_CREATION_IN_PROGRESS` | 같은 멱등성 키의 생성이 진행 중 |
| HTTP 오류 | 410 | `PREVIEW_EXPIRED` | Preview 만료 |
| Preview `conflicts[]` | 201 | `INSUFFICIENT_AVAILABLE_TIME` | 최소 일정 시간 부족 |
| Preview `conflicts[]` | 201 | `FIXED_EVENT_CONFLICT` | 고정 행사 시간 충돌 |
| Planner 오류 | 422 | `FIXED_EVENT_UNREACHABLE` | 행사 전후 실제 이동 불가 |
| Planner 오류 | 422 | `END_CONSTRAINT_UNREACHABLE` | 마지막 도착 제약 충족 불가 |

오류 응답은 `conflictDate`, `requiredMinutes`, `availableMinutes`, `adjustableFields`를 필요에 따라 포함한다.

## 14. 비기능 요구사항

- Preview p95는 외부 경로 API 없이 1초 이내를 목표로 한다.
- Preview 생성·조회는 동일한 입력에서 결정적이어야 한다.
- 일정 생성은 동일 `Idempotency-Key`에 동일 응답을 재생해야 한다.
- Preview와 생성 요청의 원문에 비밀값과 인증 헤더를 저장하지 않는다.
- `customPrompt`는 애플리케이션 로그에 남기지 않는다.
- 소비된 Preview는 일정과 함께 보존하고 만료된 미소비 Preview는 만료 24시간 후 매일 04:30 정리한다.
- 멱등성 키는 최대 128자이며 완료 기록은 24시간 보존한 뒤 매일 04:30 정리한다.
- 외부 장소는 검색만으로 저장하지 않는다.

## 15. 완료 기준

- 당일, 숙소 미정 다일, 고정 숙소 다일, 날짜별 숙소 다일 Preview가 생성된다.
- Preview 새로고침과 만료 처리가 동작한다.
- 동일 Preview·멱등성 키의 재요청으로 일정이 중복 저장되지 않는다.
- 활성 필수 질문이 누락되면 Preview가 생성되지 않는다.
- 필수 장소와 고정 행사가 생성 일정에 포함된다.
- 숙소 미정 일정은 생성 가능하며 미평가 경로 범위를 명시한다.
- 외부 장소는 Resolve 이후 내부 ID로만 Planner에 전달된다.
- API_SPEC, API_FIELD_GUIDE, ERD, migration, 테스트가 같은 계약을 사용한다.

### 프론트 1차 범위

- 날짜·시작 위치, 질문, 필수 장소, Preview, 생성, 상세·지도 흐름을 완료한다.
- 일정 상세의 일차·방문 순서·도착시각·체류시간·장소 정보가 API 응답과 일치한다.
- 지도는 서버의 마커와 실제 경로선을 표시하고, 일차 거리는 `routeLines[].distanceMeters` 합계를 사용한다.
- GPS 현재 위치, 숙소 모드 선택, 항공·기차 종료 제약, 날짜별 숙소, 고정 행사 충돌 입력, 일차별 시간·장소 조정은 `Deferred`다.
