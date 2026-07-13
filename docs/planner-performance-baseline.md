# 일정 Planner 성능 기준선

## 목적

일정 생성 리팩터링을 휴리스틱 개수로 평가하지 않고, 필수 제약 통과 여부와 사용자 품질 점수, 운영 비용으로 반복 비교한다.

## 판정 순서

1. Hard Gate를 검사한다.
2. Hard Gate를 모두 통과한 일정만 품질 점수를 계산한다.
3. 같은 후보 조합에서는 사용자 선호가 반영된 경로 비용이 가장 낮은 순서를 선택한다.
4. 품질 점수가 같으면 외부 API 호출 수와 응답시간이 낮은 구현을 우선한다.

## Hard Gate

| ID | 통과 기준 |
| --- | --- |
| `MUST_VISIT_PLACE_MISSING` | 모든 필수 방문 장소가 일정에 포함됨 |
| `EMPTY_DAY` | 여행 기간의 모든 일차에 방문 장소가 있음 |
| `INBOUND_ROUTE_MISSING` | 모든 방문 장소에 진입 경로가 있음 |
| `FINAL_ROUTE_MISSING` | 모든 일차에 해당 날짜 도착지까지 최종 경로가 있음 |
| `DAY_TIME_OVERRUN` | 체류시간과 전체 이동시간이 일차 가용시간 이내 |

Hard Gate 위반 일정은 품질 점수와 관계없이 실패다.

## 현재 품질 점수 100점

| 지표 | 배점 | 현재 계산 기준 |
| --- | ---: | --- |
| `TIME_FIT` | 30 | 일차별 가용시간 초과분 |
| `MOBILITY_FIT` | 25 | 저도보 조건, 도보 전용 이동시간, 언덕 부담 장소 |
| `TRANSIT_FIT` | 20 | 대중교통 구간과 환승 부담 |
| `PREFERENCE_FIT` | 15 | 테마 및 페이스 반영 |
| `ENDPOINT_FIT` | 10 | 모든 일차의 최종 도착 경로 |

## Iteration 001 기준선

| 시나리오 | Hard Gate | 최소 점수 | 2·3차 리팩토링 결과 | 핵심 기대 결과 |
| --- | :---: | ---: | ---: | --- |
| 부모님·여유·로컬·언덕 회피·환승 적게 | PASS | 85 | 95 | 부담 장소 제외, 로컬 장소 포함 |
| 친구·활동형·자연·빠른 이동 | PASS | 80 | 100 | 해변·산책로 우선 |
| 혼자·여유·맛집·환승 적게 | PASS | 85 | 100 | 음식점 우선, 시간 내 체류시간 보정 |
| 연인·축제·행사·환승 가능 | PASS | 80 | 100 | 행사 장소 우선, 시간 내 체류시간 보정 |
| 3일·일차별 숙소 이동 | PASS | 70 | PASS | 각 일차의 출발지·도착지 경로 완결 |

검증 명령:

```bash
scripts/run-schedule-scenario-check.sh
```

## Iteration 002 목표

- 대표 시나리오 평균 85점 이상, 최저 80점 이상
- 일차별 장소 순서를 전수 비교하고 최단시간, 저도보, 최소 환승 선호를 목적함수에 반영
- 동일 출발·도착 경로는 한 요청에서 한 번만 외부 Provider에 조회
- 외부 Provider 호출과 DB 저장 트랜잭션 분리
- 실제 운영시간 정규화 전까지 운영정보 미확인 장소는 경고를 유지

## 실제 Provider 2박 3일 기준선

2026-07-12 ODsay·TMAP 실제 실행 결과는 Hard Gate `PASS`, 품질 `65/100`, 생성시간 `11.77초`다. 경로 해석 50회 중 캐시 적중 24회, 외부 Provider 호출 24회, fallback 0건이었다. 시간·경로 완결성은 통과했지만 부모님·저도보 조건의 부담 장소 2개와 복합 환승 부담 4건 때문에 Iteration 002 목표 80점에 미달했다.

상세 결과: `docs/scenario-results/planner-evaluation-report-2026-07-12.md`

MultiDay 최적화 후 같은 시나리오는 일차별 방문 밀도 `2·2·2`를 유지하면서 Hard Gate `PASS`, 품질 `93/100`, 생성시간 `5.20초`를 기록했다. 부담 장소는 2개에서 0개, 환승은 4회에서 1회로 감소했다. 실제 외부 HTTP는 ODsay 검색 15회, loadLane 15회, TMAP 33회로 총 63회이며 실패 4회, geometry fallback 2개가 확인됐다.

### 기준선 해석 시 주의사항

- 현재 `MOBILITY_FIT`은 대중교통 경로에 포함된 도보 구간이 아니라 전체 구간이 도보인 이동만 감점한다. 개선 후 운영 지표의 전체 도보시간은 73분이지만 점수에는 도보 전용 이동 11분만 반영됐으므로 `93/100`을 릴리스 판정 점수로 사용하지 않는다.
- `MultiDayPlanOptimizer`는 일차 배치와 일차별 방문 순서를 전수 탐색하며 여행 일수와 필수 장소 수의 서버측 상한이 아직 없다. 현재 결과는 고정 2박 3일 fixture의 기준선이며 장기 일정의 성능을 보장하지 않는다.
- 실제 Provider 결과는 단일 실행값이다. p50·p95는 같은 시나리오를 반복 실행한 뒤 별도로 기록한다.

## 현재 구조 경계

| 컴포넌트 | 책임 |
| --- | --- |
| `ScheduleRequestValidator` | 날짜, 좌표, 일차 조건, 질문·답변 계약 검증 |
| `PlacePreferenceScorer` | 장소 후보의 테마·이동 부담·권역 선호 점수 계산 |
| `DayPlaceAllocator` | 각 일차 endpoint 거리와 방문 슬롯을 기준으로 장소 배치 |
| `MultiDayPlanOptimizer` | 필수 장소와 이동 제약을 보존하면서 전체 일차 배치를 전수 비교 |
| `DayRouteOptimizer` | 일차 방문 순열과 사용자 선호 기반 경로 비용 비교 |
| `ScheduleFeasibilityChecker` | 실제 경로 계산 후 체류시간 보정과 시간 적합성 계산 |
| `ScheduleHardGateEvaluator` | 필수 장소, 빈 날짜, 시간, 경로 완결성 판정 |
| `ScheduleScoreEvaluator` | Hard Gate 통과 일정의 100점 품질 평가 |
| `SchedulePersistenceService` | 외부 경로 계산 완료 후 짧은 저장 트랜잭션 |

응답·지도 변환은 아직 `ScheduleService`에 남아 있으며 다음 분리 대상이다. 후보 조회와 선호 점수 계산은 `PlaceCandidateProvider`, `PlacePreferenceScorer`로 이동했다.
