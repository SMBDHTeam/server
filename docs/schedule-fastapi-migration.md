# Schedule FastAPI Migration

## Current Runtime Path

Spring의 일정 API는 FastAPI(`data` 레포)에 위임한다. Spring에는 규칙 기반 대체 경로가 없으므로
`SCHEDULE_FASTAPI_ENABLED=false`이면 아래 엔드포인트는 `503 EXTERNAL_PROVIDER_UNAVAILABLE`을 반환한다.

- `POST /api/v1/schedule-previews`
- `GET /api/v1/schedule-previews/{previewId}`
- `POST /api/v1/schedules`
- `GET /api/v1/schedules`
- `GET /api/v1/schedules/{scheduleId}`
- `PATCH /api/v1/schedules/{scheduleId}`
- `GET /api/v1/schedules/{scheduleId}/map`

위임 지점은 `SchedulePreviewService`, `ScheduleService`, `ScheduleV2Service`이며 모두 첫 줄에서
`FastApiScheduleClient`를 호출하는 얇은 계층이다.

## 두 서비스의 경계

| 항목 | 담당 |
| --- | --- |
| Preview 검증·정규화, Planner, Repair, 실제 경로(ODsay/TMAP) | FastAPI |
| 요청 DTO 검증(`@Valid`), 오류 코드 매핑, HTTP 계약 | Spring |
| 장소 검색·resolve, TourAPI 적재, 위치·편의시설(Kakao) | Spring |
| 공유 링크 발급·조회·폐기 | Spring |
| 만료 Preview 정리 (매일 04:30 KST) | Spring (`ScheduleGenerationCleanupService`) |

두 서비스는 **같은 PostgreSQL 데이터베이스**를 사용한다. FastAPI가 `schedules`, `schedule_days`,
`schedule_stops`, `schedule_previews`, `schedule_fixed_events`, `transit_routes`,
`transit_route_lines`, `transit_segments`에 쓰고, Spring은 같은 테이블을 JPA 엔티티로 읽는다.

이 때문에 다음이 성립한다.

- 스키마의 단일 기준은 여전히 Spring의 Flyway migration이다. 컬럼을 바꾸면 FastAPI도 함께 바꿔야 한다.
- Spring은 `ddl-auto: validate`로 뜨므로 엔티티를 지우면 안 된다. 사용처가 없어 보여도 스키마 계약이다.
- 공유 기능이 `ScheduleRepository`로 일정을 직접 읽는 흐름은 정상 동작한다.

## 삭제 완료 (2026-08-10)

FastAPI가 planner를 완전히 재구현했으므로 Spring의 대응 코드를 제거했다.

| 대상 | 내용 |
| --- | --- |
| `schedule/planner` | 후보 선정, 다일 배치, 방문 순서, Repair 전략 34개 클래스 |
| `schedule/evaluation` | Hard Gate와 품질 점수 평가 |
| `transit` | `TransitRouteProvider` 추상화와 ODsay/TMAP/BIMS/Fake 구현 |
| `external/odsay`, `external/tmap`, `external/busanbims` | 경로 Provider 클라이언트 |
| `external/openai`, `external/aitheme` | 프롬프트 해석과 장소 테마 예측 |
| `schedule/service` | `ScheduleRequestValidator`, `SchedulePersistenceService`, `ScheduleCreationPersistenceService` |

같은 이유로 `application.yaml`의 `external.odsay`, `external.tmap`, `external.busan-bims`,
`app.ai-planner`, `app.place-theme-ai`, `app.schedule-planner` 설정을 제거했다.

### 주의: `.env.example`의 ODsay/TMAP 키는 지우지 않는다

`data/runtime_env.py`가 `../server/.env`와 `../server/.env.example`을 폴백으로 읽는다.
Spring이 쓰지 않더라도 로컬에서 두 레포를 나란히 두고 실행하면 FastAPI가 이 파일에서 키를 가져간다.

## 롤백

Spring 쪽 planner는 남아 있지 않다. 되돌리려면 이 삭제 커밋을 revert해야 한다.
운영 중 FastAPI 장애는 코드 롤백이 아니라 FastAPI 복구로 대응한다.
