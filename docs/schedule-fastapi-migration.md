# Schedule FastAPI Migration

## 현재 구조

Spring의 일정 API는 FastAPI(`SMBDHTeam/data`)에 위임한다. **Spring에는 규칙 기반 대체 경로가 없다.**
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

## 두 서버의 경계

| 항목 | 담당 |
| --- | --- |
| Preview 검증·정규화, Planner, Repair, 실제 경로(ODsay·TMAP) | FastAPI |
| 요청 DTO 검증(`@Valid`), 오류 코드 매핑, HTTP 계약, trace ID | Spring |
| 장소 검색·상세·resolve, TourAPI 적재 | Spring |
| 출발지·도착지 검색, 주변 편의시설 (Kakao Local) | Spring |
| 사전 질문 조회 | Spring |
| 공유 링크 발급·조회·폐기 | Spring |

`GET /schedules`만 Spring이 가공한다. FastAPI가 전체 응답을 주면 목록용 축약 응답으로 줄인다.
FastAPI가 요약 엔드포인트를 제공하면 공개 계약 변경 없이 교체할 수 있다.

## 공유 데이터베이스

두 서버는 **같은 PostgreSQL 데이터베이스**를 쓴다. FastAPI가 아래 테이블에 쓰고, Spring은 같은
테이블을 JPA 엔티티로 읽는다.

```
schedules, schedule_days, schedule_stops, schedule_previews,
schedule_fixed_events, transit_routes, transit_route_lines,
transit_segments, places, place_operating_infos
```

따라서 다음이 성립한다.

- **스키마의 단일 기준은 여전히 Spring의 Flyway migration이다.** 컬럼을 바꾸면 FastAPI도 함께 바꿔야 한다.
- Spring은 `ddl-auto: validate`로 뜨므로 **엔티티를 지우면 안 된다.** 사용처가 없어 보여도 스키마 계약이다.
- 공유 기능이 `ScheduleRepository`로 일정을 직접 읽는 흐름은 정상 동작한다.

## 배포 환경변수

Spring은 EC2의 `/opt/hackathon-dev/.env.server`를, FastAPI는 다른 파일을 쓴다.
예전에는 두 저장소가 모두 `.env.dev`를 써서 나중에 배포한 쪽이 상대의 값을 덮어썼고,
그 뒤 상대 컨테이너가 재시작하면 설정을 잃었다. 파일을 나눠 이 간섭을 없앴다.

FastAPI에 DB 환경변수가 없으면 다음이 일어난다. **API는 계속 200을 반환하므로 겉으로는 정상으로 보인다.**

- 일정이 메모리에만 저장되고 컨테이너 재시작 시 사라진다
- 후보 장소가 DB(수백 곳) 대신 JSON 폴백(15곳)으로 줄어 일정 품질이 급락한다

확인 방법:

```bash
curl -s http://127.0.0.1:8010/health
```

`schedule_candidate_source`가 `database`가 아니면 DB 환경변수가 빠진 상태다.

## Planner 제거 완료 (2026-08-11)

FastAPI가 Planner를 완전히 재구현했으므로 Spring의 대응 코드를 모두 제거했다.

| 대상 | 내용 |
| --- | --- |
| `schedule/planner` | 후보 선정, 다일 배치, 방문 순서, Repair 전략 |
| `schedule/evaluation` | Hard Gate와 품질 점수 |
| `transit` | `TransitRouteProvider` 추상화와 ODsay/TMAP/BIMS/Fake 구현 |
| `external/odsay`, `external/tmap`, `external/busanbims` | 경로 Provider 클라이언트 |
| `external/openai`, `external/aitheme` | 프롬프트 해석, 장소 테마 예측 |

같은 이유로 `application.yaml`의 `external.odsay`, `external.tmap`, `external.busan-bims`,
`app.ai-planner`, `app.place-theme-ai`, `app.schedule-planner` 설정도 제거했다.

### `.env.example`의 ODsay/TMAP 키는 지우지 않는다

`data` 저장소의 `core/runtime_env.py`가 `../server/.env`와 `../server/.env.example`을 폴백으로 읽는다.
Spring이 쓰지 않더라도 로컬에서 두 저장소를 나란히 두고 실행하면 FastAPI가 이 파일에서 키를 가져간다.

## 미해결

**만료 Preview를 지우는 주체가 없다.** Spring의 `ScheduleGenerationCleanupService`가 Planner 정리
과정에서 삭제됐고 FastAPI에도 같은 로직이 없다. `docs/schedule-generation-v2-spec.md`의
"만료 24시간 후 매일 04:30 정리" 정책이 실행되지 않는다.

## 롤백

Spring 쪽 Planner는 남아 있지 않다. 되돌리려면 삭제 커밋을 revert해야 한다.
운영 중 FastAPI 장애는 코드 롤백이 아니라 FastAPI 복구로 대응한다.
