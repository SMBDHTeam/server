# API 변경 이력

API 계약이 변경될 때마다 최신 항목을 위에 추가한다.

## 2026-07-16

- API: `POST /api/v1/schedules`, `POST /api/v1/schedule-previews`
- 구분: 변경
- 이전: 필수 방문 장소는 여행 일수당 최대 3개였고, 활동 장소 최대 3곳에 식사 슬롯 최대 2곳을 별도로 더해 하루 최대 5곳을 구성
- 이후: 필수 방문 장소는 여행 일수당 최대 5개다. 하루 전체 방문지는 식사·카페·고정 행사를 포함해 최대 5곳이며, 여행 속도와 가용시간에 따라 2~5곳을 Soft Target으로 계산한다.
- 이유: 장소 수를 억지로 채우지 않으면서도 충분한 가용시간의 빽빽한 일정을 최대 5곳까지 구성하고, 식사 항목 때문에 총 방문지 수가 목표를 넘지 않게 하기 위함
- 호환성 파괴: 아니오. 기존 요청은 그대로 유효하며, 기존에 거절되던 4~5개 필수 방문 장소 요청은 허용 범위가 확대됨
- DB/ERD: 변경 없음
- 관련 PR 또는 이슈: 없음

## 2026-07-15 (AI 일차별 장소·날짜 제안)

- API: `POST /api/v1/schedules`, `GET /api/v1/schedules/{scheduleId}`
- 구분: 응답 필드 추가 및 일정 생성 동작 변경
- 이전: 선택적 AI는 Preview의 `customPrompt`를 소프트 선호 코드로 해석하고 실제 장소 선택과 날짜 배치는 규칙 기반 Planner만 수행
- 이후: AI가 허용된 후보 ID 안에서 일차별 장소 구성을 제안하고, 규칙 기반 Top-K와 함께 실제 경로로 비교. 서버가 필수 장소·고정 행사 날짜·방문 수·식사 슬롯·시간·Hard Gate를 검증
- 운영 지표: `evaluation.operations.planningMode`, `aiPlanConfidence` 추가
- fallback: AI 비활성화·키 누락은 `RULE_BASED`, 호출·출력 검증 실패는 `AI_FALLBACK`, AI 제안 선택은 `AI_GENERATED`, AI 평가 후 규칙 후보 선택은 `AI_ASSISTED`
- 성능: AI 호출과 규칙 기반 후보 탐색을 병렬 수행하고 AI read timeout을 8초로 제한
- 이유: AI가 실제 일정 구성을 생성하면서도 외부 모델이 필수 조건과 현실 경로를 우회하지 못하도록 하기 위함
- 호환성 파괴: 아니오. 응답 필드 추가이며 기존 요청과 응답 필드는 유지
- DB/ERD: 변경 없음
- 관련 PR 또는 이슈: 없음

## 2026-07-15 (다일 환승 점수 정규화)

- API: `POST /api/v1/schedules`, `GET /api/v1/schedules/{scheduleId}`
- 구분: 평가 결과 산정 방식 변경
- 이전: `TRANSIT_FIT`이 전체 여행의 환승 부담 합계에 직접 비례해 이동 구간이 많은 다일 일정은 평균 부담이 낮아도 0점이 됨
- 이후: 환승 부담을 평가한 이동 구간 수로 나눈 구간당 평균값으로 정규화하고, `reason`에 총 부담·구간 수·구간당 부담을 반환
- 이유: 일정 길이가 아니라 각 이동의 환승 복잡도를 비교하고 리팩터링 전후 품질을 공정하게 측정하기 위함
- 호환성 파괴: 아니오. 응답 구조는 유지되며 점수와 사유 문자열이 변경될 수 있음
- DB/ERD: 변경 없음
- 관련 PR 또는 이슈: 없음

## 2026-07-15 (ODsay 요청 간 경로 TTL 캐시)

- API: 일정 생성·수정 및 지도 응답에 사용되는 ODsay 경로
- 구분: 내부 성능 변경
- 이전: 같은 좌표쌍도 일정 생성 요청마다 경로검색·상세 선형·도보 경로를 다시 조회
- 이후: 좌표쌍 기준 ODsay 최적 경로를 30분, 상세 선형·실시간 보정 결과를 5분 동안 최대 2,048개 메모리 캐시에 보관. 실패 응답은 저장하지 않음
- 이유: 외부 API 사용량과 반복 일정 생성 지연을 줄이고 실제 Provider 실행을 20초 성능 목표 안에 유지하기 위함
- 호환성 파괴: 아니오. 요청·응답 구조 변경 없음
- DB/ERD: 변경 없음
- 관련 PR 또는 이슈: 없음

## 2026-07-15 (다일 장소·날짜 배치안 실제 경로 재평가)

- API: `POST /api/v1/schedules`, `GET /api/v1/schedules/{scheduleId}`
- 구분: 응답 필드 추가 및 내부 동작 변경
- 이전: 좌표와 선호 비용으로 가장 높은 다일 장소·날짜 배치안 한 개만 확정한 뒤 일차 내부 방문 순서만 실제 경로로 재평가
- 이후: 동일 장소 집합의 날짜 배정 차이를 포함한 상위 다일 배치안을 최대 3개 보존하고, 요청당 경량 Provider 호출 예산 안에서 일차별 출발·도착 접근성은 실제 경로, 일차 내부 연결은 좌표 추정 비용으로 비교
- 운영 지표: `evaluation.operations.multiDayPlanCandidateCount`, `multiDayPlanRerankedCount` 추가
- 예외: 고정 행사가 있으면 행사 날짜 보존을 우선하고 다일 배치안 실제 재평가는 생략
- 이유: 일차별 출발지·도착지가 다른 다일 일정에서 좌표 거리와 실제 대중교통 접근성의 차이를 최종 날짜 배정에 반영하기 위함
- 호환성 파괴: 아니오. 응답 필드 추가이며 기존 요청과 응답 필드는 유지
- DB/ERD: 변경 없음
- 관련 PR 또는 이슈: 없음

## 2026-07-15 (실제 경로 기반 순서 재평가와 단거리 도보 최적화)

- API: `POST /api/v1/schedules`, `GET /api/v1/schedules/{scheduleId}`, `GET /api/v1/schedules/{scheduleId}/map`
- 구분: 응답 필드 추가 및 내부 동작 변경
- 이전: 좌표 기반 추정으로 선택한 일차별 방문 순서만 실제 경로로 확정하고 모든 승하차 연결 도보에 TMAP 선형을 요청
- 이후: 좌표 기반 상위 순서와 식사 위치가 다른 후보를 실제 ODsay 경량 경로로 요청당 최대 30회 재평가하고, 다일 일정에는 날짜별 호출 예산을 균등 배분. 선택된 경량 조회는 상세 경로 생성에 재사용하며 500m 이하 연결 도보는 직선 선형으로 표시
- 운영 지표: `evaluation.operations`에 `routeEstimateResolutionCount`, `routeEstimateCacheHitCount`, `providerEstimateCallCount`, `providerEstimateFailureCount` 추가
- 이유: 외부 API 호출 상한을 유지하면서 실제 대중교통 이동시간과 환승 기준으로 방문 순서를 고르고 단거리 도보 선형 호출 지연을 줄이기 위함
- 호환성 파괴: 아니오. 응답 필드 추가이며 기존 필드는 유지. 경로 선택과 지도 좌표는 품질 개선에 따라 달라질 수 있음
- DB/ERD: 변경 없음
- 관련 PR 또는 이슈: 없음

## 2026-07-15 (후보 풀과 식사 위치 탐색 확대)

- API: `POST /api/v1/schedules`
- 구분: 내부 동작 변경
- 이전: 목표 방문지보다 최대 4곳 큰 후보 풀을 사용하고 Provider-free 최상위 방문 순서 중심으로 실제 경로를 확정
- 이후: 후보 풀 여유를 최대 6곳으로 확대하고 점심·저녁 슬롯이 활성화된 일차는 식사 장소 위치가 다른 순서도 실제 경로 재평가 후보에 포함
- 이유: 권역이 더 좋은 장소 조합을 탐색하고 식사 시간을 지키면서 실제 대중교통 부담이 작은 방문 순서를 선택하기 위함
- 호환성 파괴: 아니오. 요청·응답 구조 변경 없음
- DB/ERD: 변경 없음
- 관련 PR 또는 이슈: 없음

## 2026-07-15 (장기 일정 beam DP와 식사 분산)

- API: `POST /api/v1/schedules`
- 구분: 내부 동작 변경
- 이전: 전체 목표가 12곳을 넘으면 단순 endpoint allocator로 전환되어 특정 일차에 음식점이 몰리거나 식사 없는 일차가 생김
- 이후: 12곳 초과 일정은 후보·필수 마스크 다양성을 보존하는 제한 폭 beam DP로 탐색하고 일차별 활성 식사 슬롯의 부족·초과를 모두 비용에 반영
- 이유: 3박 4일 또는 하루 5곳 구성에서도 날짜별 권역과 식사 균형을 유지하기 위함
- 호환성 파괴: 아니오. 요청·응답 구조 변경 없음
- DB/ERD: 변경 없음
- 관련 PR 또는 이슈: 없음

## 2026-07-15 (Planner 탐색과 실제 경로 호출 분리)

- API: `POST /api/v1/schedules`
- 구분: 내부 동작 및 운영 지표 의미 변경
- 이전: 일차 방문 순열의 모든 고유 경로 쌍을 ODsay 경량 검색으로 평가해 하루 5곳에서 초당 호출 제한 `429`가 발생할 수 있음
- 이후: 후보·순열 탐색은 Provider-free 좌표 기반 `PlannerRouteEstimator`를 사용하고 최종 선택된 구간만 실제 경로로 확정. ODsay 호출은 기본 150ms 간격으로 직렬화
- 이유: 조합 탐색 비용이 외부 API 호출량과 장애율을 증폭하지 않게 하고 ODsay의 일일·초당 호출 한도를 보호하기 위함
- 호환성 파괴: 아니오. 요청·응답 구조는 동일하며 `providerCallCount`, `routeResolutionCount`가 최종 경로 확정 호출만 집계하도록 의미를 명확히 함
- DB/ERD: 변경 없음
- 관련 PR 또는 이슈: 없음

## 2026-07-15 (식사 슬롯과 활동 슬롯 분리)

- API: `POST /api/v1/schedules`, `PATCH /api/v1/schedules/{scheduleId}`
- 구분: 동작 및 검증 변경
- 이전: 하루 최대 3곳에 점심·저녁 장소가 포함돼 종일 일정의 일반 활동이 1곳까지 줄어들 수 있음
- 이후: 일반 활동은 하루 최대 3곳을 유지하고 활성 점심·저녁 창에 식사 장소를 최대 2곳 추가. 생성·수정 결과의 하루 전체 방문지는 최대 5곳
- 이유: 식사 추천을 제공하면서도 관광·문화·자연 활동 밀도를 유지하고 긴 미사용 시간을 줄이기 위함
- 호환성 파괴: 아니오. 기존 3곳 일정은 그대로 유효하며 허용 상한만 확대
- DB/ERD: 변경 없음
- 관련 PR 또는 이슈: 없음

## 2026-07-15 (후보 선택과 날짜 배치 공동 최적화)

- API: `POST /api/v1/schedules`
- 구분: 내부 동작 변경
- 이전: `PlaceCandidateProvider`가 목표 방문지 수만큼 장소를 먼저 확정한 뒤 날짜 배치와 방문 순서만 최적화
- 이후: 목표보다 최대 4곳 큰 후보 풀을 만들고 최대 20개 후보에서 필수 장소, 식사 시간창, 추정 이동거리, 사용자 선호 비용을 기준으로 장소 선택과 날짜 배치를 비트마스크 동적계획법으로 공동 최적화
- 이유: 후보 선결정 때문에 더 높은 품질의 장소 조합을 탐색하지 못하는 구조를 제거하기 위함
- 호환성 파괴: 아니오. 요청·응답 구조 변경 없음
- DB/ERD: 변경 없음
- 관련 PR 또는 이슈: 없음

## 2026-07-15 (선택적 AI 프롬프트 해석과 fallback)

- API: `POST /api/v1/schedule-previews`, `GET /api/v1/schedule-previews/{previewId}`
- 구분: 응답 필드 추가 및 내부 동작 변경
- 이전: `customPrompt`를 고정 키워드 규칙으로만 해석하고 실제 해석 실행 방식을 응답에서 확인할 수 없음
- 이후: 설정으로 활성화한 OpenAI Responses API의 strict JSON Schema 출력으로 소프트 선호를 해석하고 `interpretedPrompt.source`, `confidence`를 반환. 키 누락·시간 초과·Provider 오류·출력 검증 실패 시 규칙 기반 fallback
- 이유: AI를 Hard Constraint와 분리해 안전하게 도입하고 실제 AI 사용 여부를 사용자와 운영자가 확인할 수 있게 하기 위함
- 호환성 파괴: 아니오. Preview 응답 필드 추가
- DB/ERD: 스키마 변경 없음. 기존 `interpreted_prompt_json`에 추가 필드를 저장
- 관련 PR 또는 이슈: 없음

## 2026-07-15 (일정 생성 실행 방식 문서 명확화)

- API: `POST /api/v1/schedules`, `POST /api/v1/schedule-previews`
- 구분: 문서 명확화 및 내부 책임 분리
- 이전: 명세에서 현재 생성기를 AI 일정 배치로 설명했지만 실제로는 키워드 규칙과 결정론적 탐색만 사용
- 이후: 현재 구현을 규칙 기반 제약 Planner로 명시하고 프롬프트 해석을 `PlanningPromptInterpreter` 경계로 분리
- 이유: 현재 기능을 AI로 오인하지 않게 하고 향후 하이브리드 AI Planner를 제약 최적화기와 독립적으로 도입하기 위함
- 호환성 파괴: 아니오. 요청·응답·상태 코드 변경 없음
- DB/ERD: 변경 없음
- 관련 PR 또는 이슈: 없음

## 2026-07-15 (점심·저녁 장소 자동 추천)

- API: `POST /api/v1/schedules`, `GET /api/v1/schedules/{id}`
- 구분: 동작 변경
- 이전: 음식점·카페도 일반 장소와 동일하게 점수화되어 식사 시간 밖에 배치되거나 후보에서 제외될 수 있음
- 이후: 일차 가용시간이 45분 이상 겹치면 점심 `11:00~14:00`, 저녁 `17:00~19:00` 창마다 음식 후보를 최대 1곳 확보하고 `arriveAt`을 해당 창 안으로 정렬. 정렬 결과를 `mealTimeSlot`, `waitingMinutesBefore`로 반환
- 이유: 종일 일정에서 점심·저녁 공백을 줄이고 음식점·카페 추천을 실제 이용 시간에 맞추기 위함
- 호환성 파괴: 아니오. 응답 필드 추가와 자동 추천 및 시간 배치 정책 변경
- DB/ERD: 변경 없음
- 관련 PR 또는 이슈: 없음

## 2026-07-15 (일정 경로 신뢰성과 종료지 정책)

- API: `GET /api/v1/places`, `POST /api/v1/places/resolve`, 일정 상세의 장소 객체
- 구분: 변경
- 이전: 카테고리 코드·원문만 반환해 프론트가 표시 문구를 자체 변환
- 이후: 사용자 표시용 `categoryLabel`을 함께 반환하고 Resolve 응답에도 카테고리·주소·이미지·외부 URL을 유지
- 이유: 검색, 선택 장소, 일정 상세에서 동일한 카테고리 계약을 사용하기 위함
- 호환성 파괴: 아니오
- 관련 PR 또는 이슈: 없음

- API: `POST /api/v1/schedules`, `GET /api/v1/schedules/{id}`
- 구분: 변경
- 이전: Planner 결정 종료지가 `null`로 남고 긴 이동·미사용 시간·경로 신뢰도를 별도로 제공하지 않음
- 이후: 종료 제약이 없는 일차는 마지막 방문지를 `LAST_STOP` 종료지로 저장하고 `startLocationSource`, `endLocationSource`, `unusedMinutes`, `longTransitWarnings`, `routeConfidence`를 반환
- 이유: null 도착 문구를 제거하고 일정 품질과 예상 경로 여부를 사용자에게 투명하게 표시하기 위함
- 호환성 파괴: 아니오. 응답 필드 추가이며 `LAST_STOP`은 별도 최종 이동이 없어 `finalTransit=null`
- 관련 PR 또는 이슈: 없음

- API: `GET /api/v1/schedules/{id}/map`
- 구분: 변경
- 이전: Provider가 거리값을 주지 않으면 `routeLines[].distanceMeters=null`
- 이후: Provider 거리값이 없으면 polyline 좌표 길이로 계산해 `distanceMeters`를 항상 정수로 반환
- 이유: 결과 화면 거리 합계를 안정적으로 계산하기 위함
- 호환성 파괴: 아니오
- 관련 PR 또는 이슈: 없음

## 2026-07-15 (로컬 프론트 CORS)

- API: `/api/v1/**` CORS preflight
- 구분: 수정
- 이전: `localhost:8080`만 허용하고 V2 생성에 필요한 `Idempotency-Key` 요청 헤더는 허용하지 않음
- 이후: `localhost:3000`, `127.0.0.1:3000`을 추가하고 `Idempotency-Key` 요청 헤더를 허용
- 이유: 로컬 Next.js 프론트가 백엔드 API를 브라우저에서 직접 호출하고 V2 일정을 생성할 수 있게 하기 위함
- 호환성 파괴: 아니오
- 관련 PR 또는 이슈: 없음

## 2026-07-15 (프론트 기본 생성 흐름 간소화)

- API: `GET /api/v1/trip-questions`
- 구분: 변경
- 이전: 질문의 표시 순서와 선택 수만 반환해 Step1~3 화면 그룹을 안정적으로 구분할 필드가 없었음
- 이후: `uiStep`을 추가하고 `COMPANION`, `MOBILITY`, `PACE`, `TRANSIT`, `THEME` 및 활성 답변 ID를 안정적인 요청 계약으로 고정
- 이유: 프론트가 질문 배열 위치에 의존하지 않으면서 Step2 전용 디자인과 다중 테마 선택을 유지하기 위함
- 호환성 파괴: 예. 기존 `PACE_BALANCED`, `THEME_LOCAL` 등 이전 seed 답변은 비활성화되며 신규 활성 ID를 사용해야 함
- DB: `V5__add_question_ui_step.sql`에서 `questions.ui_step`과 `1~3` 검사 조건 추가
- 관련 PR 또는 이슈: 없음

- API: `POST /api/v1/schedule-previews`
- 구분: 문서 명확화
- 이전: 모든 고급 입력을 기본 페이지 흐름에서 수집하는 것으로 안내
- 이후: 기본 화면은 날짜·시작 위치와 질문·필수 장소만 수집하고 `UNDECIDED` 숙소 및 빈 제약을 사용. 숙소·종료 제약·행사·일차별 조정·자유 요청은 API 지원을 유지하되 프론트 1차 범위에서 연기
- 이유: 사용자가 일정 초안을 빠르게 생성하도록 입력 단계를 줄이기 위함
- 호환성 파괴: 아니오. 요청 필드와 서버 기능은 유지
- 관련 PR 또는 이슈: 없음

- API: `GET /api/v1/schedules/{scheduleId}/map`
- 구분: 문서 명확화
- 이전: Kakao Maps SDK 사용을 특정하고 결과 거리 계산 기준이 정의되지 않음
- 이후: 지도 제공자 중립적인 WGS84 좌표 계약으로 표현하고 결과 거리는 일차별 `routeLines[].distanceMeters` 합계를 사용
- 이유: 현재 Naver 지도 구현과 API 계약을 분리하고 실제 대중교통 경로 거리를 표시하기 위함
- 호환성 파괴: 아니오
- 관련 PR 또는 이슈: 없음

## 2026-07-15 (일정 생성 V2 구현)

- API: `POST /api/v1/schedule-previews`, `GET /api/v1/schedule-previews/{previewId}`
- 구분: 추가
- 이전: 클라이언트가 일차별 시간·출발·도착 조건을 직접 조립해 일정 생성 요청에 전달
- 이후: 서버가 최소 사용자 입력, 숙소 모드, 종료 제약, 질문 답변, 필수 장소, 고정 행사, 자유 요청을 정규화해 30분 유효한 불변 Preview와 `resolvedDays`를 반환
- 이유: 숙소와 일차별 조건을 확정하지 않은 사용자도 생성 전에 서버 계산 결과와 기본값·충돌을 확인할 수 있게 하기 위함
- 호환성 파괴: 아니오. 신규 API이며 V1 생성 API를 함께 유지
- 관련 PR 또는 이슈: 없음

- API: `POST /api/v1/schedules`
- 구분: 변경
- 이전: 날짜, 전역 시간·위치, 선택 답변, 필수 장소, 선택 `days`를 한 요청으로 전달해 즉시 Planner 실행
- 이후: `Idempotency-Key` 헤더와 `previewId`만 전달하고 서버에 저장된 `READY` Preview를 한 번만 실행. 응답에서 top-level `dailyStartTime`, `dailyEndTime` 제거 및 `previewId`, `planningAssumptions` 추가
- 이유: 사용자 확인 조건과 Planner 실행 조건을 일치시키고 느린 생성 요청의 중복 저장을 방지하기 위함
- 호환성 파괴: 아니오. `Idempotency-Key` 헤더로 V2를 구분하고 헤더 없는 V1 요청을 유지
- 관련 PR 또는 이슈: 없음

- API: `GET /api/v1/schedules/{scheduleId}`, `GET /api/v1/schedules`
- 구분: 추가·변경
- 이전: 일정 단건 조회 API가 없고 전체 목록의 반환 순서가 정의되지 않음
- 이후: 저장된 상세 일정을 ID로 조회하고 목록은 `startDate ASC`, 같은 시작일은 `createdAt DESC`로 반환
- 이유: 상세 페이지 직접 접근·새로고침과 가장 가까운 여행 계산을 안정적으로 지원하기 위함
- 호환성 파괴: 아니오. 단건 조회는 신규 API이며 목록 정렬만 결정적으로 변경됨
- 관련 PR 또는 이슈: 없음

- API: `GET /api/v1/trip-questions`
- 구분: 변경
- 이전: 모든 질문이 `SINGLE_CHOICE`이고 질문당 `answerId` 하나를 전달
- 이후: `MULTIPLE_CHOICE`, `minSelections`, `maxSelections`를 지원하고 Preview 요청에서 질문별 `answerIds` 배열을 전달
- 이유: 여행 테마 등 복수 선호를 구조화해 Planner 점수에 반영하기 위함
- 호환성 파괴: 아니오. 질문 조회에 선택 수 필드를 추가하고 V2 Preview에서만 `answerIds`를 사용
- 관련 PR 또는 이슈: 없음

- API: `GET /api/v1/places`, `POST /api/v1/places/resolve`
- 구분: 변경·추가
- 이전: `GET /places`가 내부 TourAPI 장소만 반환하고 모든 결과가 내부 ID를 가짐
- 이후: `scope=ALL`, `size`로 내부·Kakao 후보를 통합 반환하고 외부 후보는 `placeId: null`로 표시. 사용자가 선택한 외부 후보만 Resolve해 내부 ID 반환
- 이유: 내부 적재 데이터에 없는 장소도 필수 방문 장소와 고정 행사 위치로 사용할 수 있게 하면서 검색만으로 DB를 오염시키지 않기 위함
- 호환성 파괴: 아니오. V2 필드를 추가하고 호환 기간에 기존 `id`, `externalContentId` 별칭을 함께 반환
- 관련 PR 또는 이슈: 없음

- 문서: `docs/schedule-generation-v2-spec.md`, `docs/frontend-schedule-v2-handoff.md`, `docs/ERD.md`
- 구분: 추가·변경
- 이전: 숙소 미정, Preview 생명주기, 자유 요청 해석, 고정 행사, 멱등성 저장 구조가 정의되지 않음
- 이후: `UNDECIDED`, `FIXED_BASE`, `PER_NIGHT` 숙소 모드와 Preview·멱등성·고정 행사 데이터 모델, V4 migration과 프론트 연동 기준 구현
- 이유: 백엔드 구현 전에 제품, API, 데이터, 프론트 책임을 같은 기준으로 합의하기 위함
- 호환성 파괴: 아니오. 새 테이블과 nullable 확장을 V4 migration으로 추가
- 관련 PR 또는 이슈: 없음

## 작성 형식

```md
## YYYY-MM-DD

- API: `METHOD /api/v1/...`
- 구분: 추가 | 변경 | 삭제 | 수정
- 이전:
- 이후:
- 이유:
- 호환성 파괴: 예 | 아니오
- 관련 PR 또는 이슈:
```

## 2026-07-14

- API: `POST /api/v1/schedules`
- 구분: 수정
- 이전: 장소 방문 순서를 비교하는 모든 경로 해석에서 최종 저장용 상세 경로 Provider를 호출
- 이후: 후보 순서 비교에서는 경량 경로를 사용하고, 선택된 방문 순서는 기존 ODsay 검색 결과를 재사용해 상세 선형·실시간·도보 정보만 보강. 응답 필드는 유지하며 `evaluation.operations.providerCallCount`, `routeResolutionCount`, `routeCacheHitCount`는 탐색·상세화 단계 합계로 집계
- 이유: 일정 생성 중 외부 `loadLane`, 도보 경로, 실시간 보정 호출을 후보 조합 수만큼 반복하지 않고 생성시간과 외부 API 사용량을 줄이기 위함
- 호환성 파괴: 아니오
- 관련 PR 또는 이슈: 없음

## 2026-07-13

- API: `POST /api/v1/schedules`
- 구분: 변경
- 이전: 여행 기간과 필수 방문 장소 수에 명시적인 상한이 없어 다일 최적화 탐색 시간이 급증할 수 있음
- 이후: 여행 기간은 최대 4일, `mustVisitPlaceIds`는 중복 없이 여행 일수당 최대 3개로 제한하고 초과 시 `400 INVALID_SCHEDULE_CONDITION` 반환
- 이유: 정확 탐색 Planner의 조합 수와 외부 경로 API 호출량을 예측 가능한 범위로 제한
- 호환성 파괴: 예. 상한을 넘거나 중복 필수 방문지를 포함한 기존 요청은 거절됨
- 관련 PR 또는 이슈: 없음

- API: `GET /api/v1/schedules`, `PATCH /api/v1/schedules/{scheduleId}`
- 구분: 추가
- 이전: API 문서에 일정 목록과 수정 계약만 있고 Controller·Service 구현이 없음
- 이후: 전체 일정 목록을 반환하고, 전체 방문 계획 기준으로 장소 추가·삭제·일차·순서·체류시간 변경 후 전체 대중교통 경로를 재계산
- 이유: 생성된 일정을 다시 조회하고 사용자가 편집할 수 있는 1차 스프린트 흐름 완성
- 호환성 파괴: 아니오
- 관련 PR 또는 이슈: 없음

- API: `POST /api/v1/schedules/{scheduleId}/shares`, `GET /api/v1/shared-schedules/{token}`, `GET /api/v1/shared-schedules/{token}/map`, `DELETE /api/v1/schedules/{scheduleId}/shares/{shareId}`
- 구분: 추가
- 이전: API 문서와 ERD에 공유 계약만 있고 구현이 없음
- 이후: 32바이트 공유 토큰 생성, SHA-256 해시 저장, 읽기 전용 일정·지도 조회, 만료·폐기 검증과 링크 폐기를 지원
- 이유: 인증 없이 특정 일정을 안전하게 읽기 전용으로 전달하는 1차 스프린트 공유 흐름 완성
- 호환성 파괴: 아니오
- 관련 PR 또는 이슈: 없음

## 2026-07-12

- API: `POST /api/v1/schedules`
- 구분: 변경
- 이전: 생성 응답에서 일정과 경로만 반환하고 Hard Gate·품질 점수·외부 Provider 실행 비용은 테스트에서만 확인
- 이후: `evaluation`으로 Hard Gate, 100점 품질 지표, 생성시간·경로 캐시·Provider 호출, ODsay 경로검색·loadLane·TMAP 실제 HTTP 호출, 경로·geometry fallback 운영 지표를 반환
- 이유: 실제 다일 일정 생성 품질과 성능을 같은 기준으로 반복 측정하기 위함
- 호환성 파괴: 아니오. 기존 필드는 유지하고 평가 필드를 추가함
- 관련 PR 또는 이슈: 없음

- API: `POST /api/v1/schedules`
- 구분: 변경
- 이전: 전역 `startLocation`, `endLocation`, `dailyStartTime`, `dailyEndTime`을 모든 일차에 동일하게 적용
- 이후: 선택 필드 `days`로 각 일차의 출발지, 도착지, 시작시각, 종료시각을 전달하고 응답의 각 일차에도 `startLocation`, `endLocation`을 반환. 필수 방문 장소 외 남은 슬롯은 자동 추천으로 채우며 실제 이동시간 기준으로 체류시간을 조정
- 이유: 2박 3일처럼 숙소 이동과 첫날·마지막 날 가용시간 차이가 있는 다일 일정을 지원하기 위함
- 호환성 파괴: 아니오. `days` 생략 시 기존 전역 조건 적용
- 관련 PR 또는 이슈: 없음

- API: `POST /api/v1/schedules`
- 구분: 수정
- 이전: `selectedAnswers`가 비어 있지 않은지만 검증하고 질문·답변 관계와 필수 질문 누락은 검사하지 않음
- 이후: 활성 필수 질문별 단일 답변, 질문·답변 소속 관계, 중복 질문을 검증
- 이유: 의미 없는 문자열 답변이 일정 추천 휴리스틱에 전달되는 것을 차단하기 위함
- 호환성 파괴: 예. 필수 질문이 누락되거나 잘못 연결된 기존 요청은 `400 INVALID_SCHEDULE_CONDITION` 반환
- 관련 PR 또는 이슈: 없음

## 2026-07-08

- API: `POST /api/v1/schedules`
- 구분: 변경
- 이전: 일정 생성 응답이 방문 순서, 체류시간, 총 이동시간, 요금, 노선명, 승하차명 중심으로 반환됨
- 이후: 일자별 시작/종료시각, 하루 요약, 방문지 도착/출발 예정시각, 장소 카테고리·주소·대표이미지·운영정보, 경로 요약, 경로 출발/도착 예정시각, 도보시간, 대기시간, 환승 횟수, provider, 실시간 반영 상태, fallback 여부, 구간 안내문, 구간 소요시간·거리·정류장 수, 추천 이유, 주의사항을 반환
- 이유: 생성된 일정을 내부 평가가 아니라 사용자가 그대로 따라갈 수 있는 상세 일정으로 전달하기 위함
- 호환성 파괴: 아니오. 기존 필드는 유지하고 상세 필드를 추가함
- 관련 PR 또는 이슈: 없음

- API: `GET /api/v1/schedules/{scheduleId}/map`
- 구분: 변경
- 이전: 지도 응답의 마커와 경로선이 좌표, 순서, 노선명, 출발·도착 지점명 중심으로 반환됨
- 이후: 마커에 도착/출발 예정시각, 보조 문구, 위험 수준을 추가하고, 경로선에 소요시간, 거리, 안내문, fallback 여부를 추가
- 이유: 지도 화면에서 일정 타임라인과 경로 설명을 함께 보여주기 위함
- 호환성 파괴: 아니오. 기존 필드는 유지하고 상세 필드를 추가함
- 관련 PR 또는 이슈: 없음

## 2026-07-02

- API: `GET /api/v1/schedules/{scheduleId}/map`, `GET /api/v1/shared-schedules/{token}/map`
- 구분: 추가
- 이전: `routeLines`에서 이동수단, 노선명, 좌표만 확인 가능
- 이후: `routeLines[].startName`, `routeLines[].endName`으로 각 선 조각의 출발·도착 지점명 반환
- 이유: 지도에서 도보, 정류장, 역 구간을 사용자가 구분할 수 있게 하기 위함
- 호환성 파괴: 아니오
- 관련 PR 또는 이슈: 없음

- API: `GET /api/v1/schedules/{scheduleId}/map`, `GET /api/v1/shared-schedules/{token}/map`
- 구분: 변경
- 이전: `mode: "WALK"` 경로선은 출발·도착 좌표를 잇는 fallback 선만 반환
- 이후: TMAP 보행자 경로 좌표를 우선 반환하고, 실패 시 기존 fallback 선을 반환
- 이유: 지도에서 도보 이동 구간을 실제 보행 경로에 가깝게 표시하기 위함
- 호환성 파괴: 아니오
- 관련 PR 또는 이슈: 없음

- API: `GET /api/v1/schedules/{scheduleId}/map`, `GET /api/v1/shared-schedules/{token}/map`
- 구분: 변경
- 이전: `routeLines`에는 대중교통 노선 좌표만 포함될 수 있었음
- 이후: 대중교통 승하차 전후 도보 구간도 기존 `routeLines` 배열에 `mode: "WALK"` 항목으로 포함
- 이유: 지도에서 출발지-승차 지점, 하차 지점-목적지 이동이 끊겨 보이지 않게 하기 위함
- 호환성 파괴: 아니오
- 관련 PR 또는 이슈: 없음

## 2026-06-25

- API: 1차 스프린트 전체 API
- 구분: 추가
- 이전: 저장소 내 기준 API 명세 없음
- 이후: `docs/API_SPEC.md`와 `docs/API_FIELD_GUIDE.md`를 기준 계약으로 지정
- 이유: 백엔드 구현과 AI Agent 작업 기준 통일
- 호환성 파괴: 아니오
- 관련 PR 또는 이슈: 없음
