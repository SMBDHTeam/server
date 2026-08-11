# 부산 여행 일정 서버: Claude 작업 인계서

이 파일은 Claude Code가 이 저장소에서 작업을 이어갈 때 읽는 시작 문서다. 세부 계약은 아래 기준 문서를 우선하며, 이 파일은 프로젝트 전반의 맥락과 현재 작업 상태를 요약한다.

## 1. 프로젝트 목표

부산 여행자가 날짜, 시작 위치, 여행 성향, 필수 방문지 같은 조건을 입력하면 실제 대중교통을 고려한 당일 또는 최대 4일 일정을 생성하는 서비스다.

**이 저장소는 Spring Boot 서버다. Planner는 더 이상 여기 없다.**

일정 생성·조회는 FastAPI 서버(`SMBDHTeam/data`)에 위임한다. Preview 검증, 후보 선택, 날짜 배치, 방문 순서, 실제 경로(ODsay·TMAP), Repair가 전부 그쪽에 있다.

```text
사용자 입력
-> Spring:  요청 DTO 검증, HTTP 계약
-> FastAPI: Preview 정규화 -> Planner -> Repair -> 실제 경로
-> 공유 DB(PostgreSQL)에 저장
-> Spring:  응답 전달, 오류 코드 매핑
```

Spring이 직접 담당하는 것은 다음이다.

- 장소 검색·상세·외부 장소 resolve, TourAPI 증분 적재
- 출발지·도착지 검색, 주변 편의시설 (Kakao Local)
- 사전 질문 조회
- 공유 링크 발급·조회·폐기
- 요청 검증(`@Valid`), 오류 코드 매핑, trace ID

두 서버는 **같은 PostgreSQL 데이터베이스**를 쓴다. Flyway 스키마의 단일 기준은 여전히 Spring이며, 컬럼을 바꾸면 FastAPI도 함께 바꿔야 한다.

## 2. 기술과 실행 환경

| 항목 | 값 |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 4.0.6 |
| Build | Gradle (`./gradlew`) |
| Local DB | Docker PostgreSQL, host port `5433` |
| Schema migration | Flyway, local/dev profile |
| Test DB | H2, 일부 PostgreSQL migration integration test |
| API base path | `/api/v1` |
| Local Swagger | `http://localhost:8080/swagger-ui.html` (`local` profile) |

기본 실행:

```bash
docker compose -f docker-compose.local.yml up -d postgres
./gradlew bootRun --args='--spring.profiles.active=local'
./gradlew test
```

실제 대중교통을 포함한 로컬 Planner 확인:

```bash
SERVER_PORT=8080 scripts/run-local-live-transit.sh
python3 -m http.server 8080 --bind 127.0.0.1 --directory docs/examples
# http://localhost:8080/planner-console/
```

- 실제 키는 `.env`에만 두며 Git, 문서, 로그, 테스트 fixture에 남기지 않는다.
- `output/`은 사람이 확인한 PDF/PNG 산출물이다. 서버 소스 변경과 함께 커밋하지 않는다.

## 3. 작업 전 필수 문서와 규칙

다음 순서로 읽고 코드와 대조한다.

1. `AGENTS.md`
2. `COLLABORATION.md`
3. `docs/ai-harness.md`
4. `docs/API_SPEC.md`
5. `docs/API_FIELD_GUIDE.md`
6. `docs/ERD.md`
7. `docs/api-guidelines.md`
8. `docs/spring-guidelines.md`
9. `docs/testing-guidelines.md`
10. `docs/review-checklist.md`

필수 원칙:

- `docs/API_SPEC.md`는 HTTP 계약, `docs/ERD.md`는 데이터 설계의 단일 기준이다.
- API 변경은 같은 작업에서 `API_SPEC.md`, `API_FIELD_GUIDE.md`, `api-change-log.md`, 테스트를 갱신한다.
- Entity/컬럼/관계 변경은 migration과 `ERD.md`를 함께 갱신한다. 이미 공유된 migration은 수정하지 않는다.
- Entity와 외부 Provider DTO를 API 응답으로 직접 노출하지 않는다.
- Controller는 HTTP, Service는 비즈니스/트랜잭션, Repository는 영속성, Client는 외부 API를 담당한다.
- 외부 API 호출을 긴 DB 트랜잭션 안에 넣지 않는다.
- 요청하지 않은 CI/CD, 배포, 인프라 변경은 금지다.
- 인증, 사용자 도메인, edit token, 일정 `version`은 1차 스프린트 범위 밖이다.

## 4. 주요 도메인과 패키지

```text
question/      사전 질문 및 답변 seed/조회
place/         내부 장소 검색, 외부 장소 resolve, TourAPI 증분 적재
location/      Kakao Local 출발/도착 위치 검색
schedule/      일정·Preview 위임 계층, DTO, 엔티티
facility/      Kakao 기반 주변 편의시설 실시간 조회
share/         공유 링크 생성, 조회, 폐기
external/      Kakao, TourAPI, FastAPI 클라이언트
common/        CORS, Security, trace ID, 예외 처리, Swagger 설정
```

일정 관련 클래스는 모두 얇은 위임 계층이다.

| 컴포넌트 | 책임 |
| --- | --- |
| `SchedulePreviewService` | Preview 생성·조회를 FastAPI에 위임 (43줄) |
| `ScheduleV2Service` | Idempotency-Key 검증 후 위임 |
| `ScheduleService` | 일정 생성·조회·수정·지도를 위임. 목록만 축약 응답으로 변환 |
| `FastApiScheduleClient` | 위임 HTTP 호출과 오류 코드 매핑 |

`app.schedule-fastapi.enabled`가 꺼져 있으면 위 엔드포인트는 `503 EXTERNAL_PROVIDER_UNAVAILABLE`을 반환한다. **Spring에 규칙 기반 대체 경로는 없다.**

`Schedule`, `ScheduleDay`, `ScheduleStop`, `SchedulePreview` 엔티티는 남아 있다. FastAPI가 같은 테이블에 쓰고 Spring이 `ddl-auto: validate`로 뜨므로, 사용처가 없어 보여도 **스키마 계약이다. 지우면 기동이 깨진다.**

## 5. API 요약

모든 경로는 `/api/v1` 아래에 있다. 1차 스프린트는 인증이 없다.

| 영역 | Method | Endpoint | 설명 |
| --- | --- | --- | --- |
| 질문 | `GET` | `/trip-questions` | 화면 Step별 질문/답변 조회 |
| 위치 | `GET` | `/locations/search` | Kakao Local 위치 검색 |
| 장소 | `GET` | `/places` | 내부 또는 `scope=ALL` 통합 검색 |
| 장소 | `POST` | `/places/resolve` | 선택한 Kakao 장소를 내부 ID로 upsert |
| 장소 | `GET` | `/places/{placeId}` | 장소 상세 |
| 편의시설 | `GET` | `/places/{placeId}/nearby-facilities` | 실시간 주변 시설 |
| Preview | `POST` | `/schedule-previews` | V2 조건 검증/일차별 조건 계산 |
| Preview | `GET` | `/schedule-previews/{previewId}` | Preview 재조회 |
| 일정 | `POST` | `/schedules` | V1 또는 Preview 기반 V2 생성 |
| 일정 | `GET` | `/schedules`, `/schedules/{id}` | 목록/단건 조회 |
| 일정 | `PATCH` | `/schedules/{id}` | 방문지 편집 및 경로 재계산 |
| 지도 | `GET` | `/schedules/{id}/map?dayNo=` | 마커와 경로선 |
| 공유 | `POST/GET/DELETE` | `/schedules/{id}/shares`, `/shared-schedules/{token}` | 공유 링크 lifecycle |

### V2 생성 계약

신규 프론트는 다음 흐름을 사용한다.

```text
GET /trip-questions
GET /locations/search
GET /places?scope=ALL
POST /places/resolve                 (외부 Kakao 장소를 선택한 경우)
POST /schedule-previews
POST /schedules + Idempotency-Key    (body: { "previewId": "..." })
GET /schedules/{scheduleId}
GET /schedules/{scheduleId}/map
```

- `POST /schedules`는 `Idempotency-Key`가 있으면 V2, 없으면 V1 호환 요청으로 분기한다.
- V2에서 `lodgingPlan`은 필수이며, 숙소 미정 기본값은 `{ "mode": "UNDECIDED" }`다.
- `selectedAnswers`는 질문별 `answerIds[]` 구조다. 질문/답변 문자열 ID는 화면과 점수 계산에서 사용하는 안정 계약이다.
- Preview 유효시간은 30분이며, 같은 Preview는 일정 하나만 생성할 수 있다.

## 6. FastAPI 위임 계약

일정 생성 로직은 이 저장소에 없다. Planner 정책, 제약 우선순위, 탐색과 품질 기준은
FastAPI 저장소(`SMBDHTeam/data`)를 봐야 한다.

Spring 쪽에서 알아야 할 것은 다음이다.

| 항목 | 값 |
| --- | --- |
| 설정 | `app.schedule-fastapi.*` (`SCHEDULE_FASTAPI_ENABLED`, `SCHEDULE_FASTAPI_BASE_URL`) |
| base-url | 끝에 슬래시를 붙이지 않는다. 붙이면 `//api/v1/...`로 나가 404가 된다 |
| 위임 대상 | Preview 생성·조회, 일정 생성·목록·단건·수정·지도 |
| 꺼져 있을 때 | `503 EXTERNAL_PROVIDER_UNAVAILABLE` |
| 오류 매핑 | `FastApiScheduleClient`가 400/404/409/410/422를 도메인 코드로 변환 |

`GET /schedules`만 Spring이 가공한다. FastAPI가 전체 응답을 주면 축약 응답으로 줄인다.
FastAPI가 요약 엔드포인트를 제공하면 공개 계약 변경 없이 교체할 수 있다.

V2 제품 계약: `docs/schedule-generation-v2-spec.md`
위임 구조와 두 서버의 경계: `docs/schedule-fastapi-migration.md`

`docs/planner-performance-baseline.md`와 `docs/ai-planner-roadmap.md`는 Spring에 Planner가
있던 시절의 기록이다. 현재 코드와 대응하지 않는다.

## 7. 외부 Provider와 운영 주의사항

| Provider | 쓰는 곳 | 주의 |
| --- | --- | --- |
| TourAPI | **Spring** 장소 증분 적재 | 일일 예산 900회. 개발 서버는 매일 04:00 KST |
| Kakao Local | **Spring** 위치 검색, 외부 장소, 주변 편의시설 | 선택한 외부 장소만 내부 DB에 resolve |
| ODsay | **FastAPI** 대중교통 경로 | Spring에는 더 이상 클라이언트가 없다 |
| TMAP | **FastAPI** 보행 경로 | 〃 |

TourAPI 적재는 두 단계다.

| 단계 | 호출 | 채우는 값 |
| --- | --- | --- |
| 발견 | `areaBasedList2` 페이지당 1회 | 이름·좌표·카테고리·콘텐츠유형 |
| 보강 | 장소당 3회 | 소개글·운영정보·이미지 |

**일정 생성이 쓰는 값은 발견 단계에서 전부 채워진다.** 보강 값은 장소 상세 화면 표시
전용이며 Planner는 참조하지 않는다. `TOUR_API_ENRICHMENT_ENABLED=false`로 끄면 같은
예산으로 약 3배 많은 장소를 발견할 수 있다.

- `TOUR_API_PLACE_INGESTION_ENABLED`는 개발 서버 배포 시 `false`여야 한다. 스케줄러만 켜서 변경분을 동기화한다.
- 배포 환경변수 파일은 `/opt/hackathon-dev/.env.server`다. FastAPI 저장소는 다른 파일을 쓴다. 예전에 두 저장소가 같은 파일을 써서 서로 덮어쓴 적이 있다.

## 8. 현재 상태와 이어서 할 작업 (2026-08-11)

`main`이 기준 브랜치이며 별도 진행 중인 장기 브랜치는 없다.

작업 트리에 사용자의 미커밋 변경이 남아 있을 수 있다. `git status --short`를 먼저 읽고,
**커밋할 파일을 하나씩 경로로 지정한다.** `git add -A`나 디렉터리 단위 스테이징은
사용자의 미커밋 파일을 함께 담을 수 있다.

`output/`은 사람이 확인한 PDF/PNG 산출물이다. 서버 소스 변경과 함께 커밋하지 않는다.

### 최근에 끝난 일

- Planner와 경로 Provider 계층 제거. 소스가 189파일 18,098줄에서 111파일 7,722줄로 줄었다.
- 모든 오류 응답이 `code`·`fieldErrors`·`traceId`를 갖도록 핸들러 6종 추가. 신설 코드는
  `INVALID_PLACE_SEARCH_REQUEST`, `MALFORMED_REQUEST`, `RESOURCE_NOT_FOUND`, `INTERNAL_ERROR`.
- PR 테스트 게이트(`.github/workflows/test.yml`)와 배포 헬스체크 확장.
- dev Swagger 노출. `https://api.busantour.site/swagger-ui.html`
- 장소 상세에 `source`·`categoryLabel`·`placeUrl`·`primaryImageUrl` 추가.
- `resolve`의 부분 문자열 이름 매칭 제거. "해운대"가 "해운대 빛축제"에 연결되던 문제.
- TourAPI 상세 보강 토글 추가 (`TOUR_API_ENRICHMENT_ENABLED`, 기본 `true`).

### 확인이 필요한 것

**만료 Preview를 지우는 주체가 없다.** `ScheduleGenerationCleanupService`가 Planner 정리
과정에서 삭제됐고 FastAPI에도 같은 로직이 없다. `docs/schedule-generation-v2-spec.md`에는
"만료 24시간 후 매일 04:30 정리"가 그대로 남아 있다. Spring에 복원할지, FastAPI가 맡을지,
정책을 접을지 정해야 한다.

이 때문에 `SchedulePreviewRepository`, `ScheduleCreationRequestRepository`,
`ScheduleCreationRequest` 엔티티가 미사용 상태다. `schedule_creation_requests` 테이블은
FastAPI도 쓰지 않는다.

**과거 데이터 정합성.** FastAPI의 결정적 UUID에 일정 식별자가 빠져 있어, 날짜가 겹치는
일정들이 같은 `schedule_days` 행을 공유했을 수 있다. 원인은 고쳤지만 이미 저장된 50여 건은
점검하지 않았다.

### 프론트 확인 후 진행할 것

- `nearby-facilities` 미지원 유형에 `501` 대신 `400` 반환. 프론트가 `501`로 분기하는지 확인 필요.
- 장소 검색 응답의 중복 필드(`placeId`/`id`, `externalId`/`externalContentId`) 정리.
- 장소 상세 화면을 카카오·네이버 지도로 넘기는 방향이 정해졌다. 전환이 끝나면
  `TOUR_API_ENRICHMENT_ENABLED=false`로 적재 보강을 끌 수 있다.

### 인증 도입과 함께

`GET /schedules`에 페이징과 사용자 스코프가 없어 전체 사용자의 일정이 반환된다.
`users` 테이블은 있으나 서비스·컨트롤러가 없는 사전 작업 상태다.

## 9. 검증과 완료 보고

최소 검증:

```bash
./gradlew test --tests '<관련 테스트 클래스>'
./gradlew test
```

외부 API 실제 호출은 단위 테스트와 분리한다. 테스트에는 Stub/Mock을 사용하고, 실제 Kakao/TourAPI 확인은 수동 API 테스트로 기록한다. 일정 생성은 FastAPI가 떠 있어야 하므로 로컬에서는 `SCHEDULE_FASTAPI_BASE_URL`을 실행 중인 FastAPI로 지정한다.

완료 보고에는 반드시 다음을 포함한다.

1. 바꾼 기능과 파일
2. 실행한 테스트와 결과
3. API/ERD/migration 변경 여부
4. 남은 가정, Provider 제한, 후속 작업
