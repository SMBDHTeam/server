# 일정 생성 시나리오 자동 평가

## 목적

기획서의 "부산 대중교통 기반 하루 일정 추천"이 질문-답변 조건을 실제 일정 생성에 반영하는지 반복 검증한다.

## 1차 Seed 질문

| 질문 ID | 답변 ID |
| --- | --- |
| `COMPANION` | `COMPANION_SOLO`, `COMPANION_COUPLE`, `COMPANION_FRIENDS`, `COMPANION_PARENTS`, `COMPANION_FAMILY_WITH_CHILD` |
| `PACE` | `PACE_RELAXED`, `PACE_BALANCED`, `PACE_ACTIVE` |
| `THEME` | `THEME_LOCAL`, `THEME_FOOD`, `THEME_HISTORY_CULTURE`, `THEME_NATURE`, `THEME_NIGHT_VIEW`, `THEME_EVENT` |
| `MOBILITY` | `MOBILITY_AVOID_HILLS_STAIRS`, `MOBILITY_LOW_WALK`, `MOBILITY_NORMAL`, `MOBILITY_OK_HILLS` |
| `TRANSIT` | `TRANSIT_SIMPLE`, `TRANSIT_FAST`, `TRANSIT_TRANSFER_OK` |

## Iteration 001 시나리오 세트

공통 조건은 부산역 출발, 김해국제공항 또는 부산역 도착, 대중교통 기반 자동 장소 선택이다.

| 테스트 | 선택 답변 | 기대 결과 | 최소 점수 |
| --- | --- | --- | ---: |
| `createAndScoreParentRelaxedLocalScenario` | 부모님, 여유, 로컬, 언덕·계단 피하기, 환승 적게 | `감천문화마을` 제외, 로컬 장소 포함 | 85 |
| `createAndScoreActiveNatureFastScenario` | 친구, 많이 둘러보기, 바다·자연, 언덕도 괜찮음, 빠른 이동 | 해변·산책로 후보 포함 | 80 |
| `createAndScoreFoodScenario` | 혼자, 여유, 맛집, 보통, 환승 적게 | 음식점 후보 1순위 선택, 체류 75분 | 85 |
| `createAndScoreEventScenario` | 연인, 적당히, 축제·행사, 보통, 환승 괜찮음 | 행사 후보 1순위 선택, 체류 90분 | 80 |

## 점수 평가 기준

| 항목 | 배점 | 기준 |
| --- | ---: | --- |
| `TIME_FIT` | 30 | 체류시간 + 이동시간이 각 일차의 시작·종료시각 안에 들어오는지 |
| `MOBILITY_FIT` | 25 | 부모님/아이/저도보/언덕회피 조건에서 도보 전용 이동과 부담 장소를 줄였는지 |
| `TRANSIT_FIT` | 20 | `TRANSIT_SIMPLE` 조건에서 복합 환승 부담을 줄였는지 |
| `PREFERENCE_FIT` | 15 | 페이스와 테마 답변이 장소명/방문 수에 반영됐는지 |
| `ENDPOINT_FIT` | 10 | 모든 일차에 해당 날짜 도착지까지의 `finalTransit`이 포함됐는지 |

## 자동화 루틴

```bash
scripts/run-schedule-scenario-check.sh
```

루틴은 다음 테스트를 실행한다.

- `QuestionSeedInitializerTest`: 질문/답변 seed가 5개 질문, 21개 답변으로 들어가며 재실행해도 중복되지 않는지 확인
- `ScheduleRequestValidatorTest`: 활성 필수 질문, 질문·답변 소속 관계, 중복 응답을 검증하는지 확인
- `DayRouteOptimizerTest`: 일차 내 모든 방문 순서 중 사용자 비용이 가장 낮은 순서를 선택하는지 확인
- `ScheduleServiceTest.createUsesDaySpecificEndpoints`: 날짜별 출발·도착 조건으로 경로를 생성하는지 확인
- `ScheduleServiceTest.createOptimizesVisitOrderWithRouteCache`: 경로 쌍 캐시와 최적 순서가 함께 적용되는지 확인
- `ScheduleServiceTest.*Scenario`: 여러 일정 생성 시나리오가 기대 장소와 점수 기준을 통과하는지 확인

## 다음 Iteration 후보

- 실제 DB seed 적용 후 `GET /api/v1/trip-questions` 응답 스냅샷 저장
- ODsay/TMAP 실제 응답을 사용하는 수동 검증 시나리오와 점수 기록
- `THEME_NIGHT_VIEW`, `TRANSIT_FAST` 실제 경로 시간 비교 시나리오 추가
