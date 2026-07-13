# 일정 생명주기 통합 테스트

## 목적

1차 스프린트 일정 API의 생성 이후 흐름이 Controller, Service, JPA, 경로 Provider, JSON 계약을 거쳐 완결되는지 검증한다.

## 실행 환경

- Spring Boot 통합 컨텍스트
- H2 테스트 DB
- 정상 경로: `FakeTransitRouteProvider`
- Provider 실패 경로: `@MockitoBean TransitRouteProvider`
- Spring Security Filter 포함 MockMvc
- E2E·서비스 통합 테스트는 트랜잭션 rollback, Provider 실패 테스트는 명시적 DB 정리

## 시나리오

| ID | 시나리오 | 주요 검증 | 통과 기준 |
| --- | --- | --- | --- |
| `SLI-001` | 전체 생명주기 정상 흐름 | 생성, 목록, 수정, 공유 생성, 공유 일정·지도, 폐기 | 모든 성공 상태 코드와 응답 계약 일치 |
| `SLI-002` | 잘못된 수정 요청 rollback | 하루 4곳 요청, 기존 일정 재조회 | `400 INVALID_SCHEDULE_CONDITION`, 기존 stop ID 보존 |
| `SLI-003` | 공유 토큰 보안 | 원본 토큰 응답, DB 저장값 확인 | DB에는 64자 SHA-256 hex만 저장 |
| `SLI-004` | 공유 링크 폐기 | 폐기 전 조회, 폐기, 폐기 후 재조회 | `200 → 204 → 404 SHARE_LINK_NOT_FOUND` |
| `SLI-005` | 일정 수정 영속성 | 기존 stop 이동, 삭제, 신규 장소 추가, 경로 재생성 | 기존 ID 보존, 삭제 반영, 일차별 `stops + 1` 경로 |
| `SLI-006` | Provider 실패 rollback | stop 삭제·추가 flush 후 경로 Provider 실패 | 기존 stop ID·순서·체류시간 완전 보존 |

## 자동화 테스트

- `ScheduleLifecycleE2ETest.completeLifecycle`
- `ScheduleLifecycleE2ETest.invalidUpdateRollsBack`
- `ScheduleLifecycleIntegrationTest.updateRebuildsScheduleAndRoutes`
- `ScheduleLifecycleIntegrationTest.shareLinkLifecycle`
- `ScheduleUpdateRollbackIntegrationTest.providerFailureRollsBackStopChanges`

## 실행 명령

```bash
./gradlew test --tests 'com.server.schedule.controller.ScheduleLifecycleE2ETest' \
  --tests 'com.server.schedule.service.ScheduleLifecycleIntegrationTest' \
  --tests 'com.server.schedule.service.ScheduleUpdateRollbackIntegrationTest'
```

## 실행 결과

- 실행일: 2026-07-13
- 결과: `BUILD SUCCESSFUL`
- 테스트: 5개 통과, 실패 0개

| ID | 결과 | 확인값 |
| --- | :---: | --- |
| `SLI-001` | PASS | 생성 `201`, 목록·수정·공유 조회 `200`, 공유 생성 `201` |
| `SLI-002` | PASS | 잘못된 수정 `400`, 재조회 stop ID 동일 |
| `SLI-003` | PASS | 원본 토큰과 DB의 64자 hash가 다름 |
| `SLI-004` | PASS | 폐기 `204`, 폐기 후 조회 `404` |
| `SLI-005` | PASS | 기존 stop ID 보존, 삭제·추가·일차 이동 및 경로 재생성 |
| `SLI-006` | PASS | Provider 실패 후 기존 stop ID와 체류시간 보존 |
