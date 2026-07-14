# 일정 생성 테스트 전략

## 목표

일정 생성의 제약조건, 품질, 저장 결과, 실제 외부 Provider 연동을 서로 독립적으로 검증한다. 테스트 실패 시 원인이 Planner 회귀인지, 외부 API 계약 변경인지, 개발 서버 배포 문제인지 바로 구분할 수 있어야 한다.

## 테스트 계층

| 계층 | 실행 시점 | 환경 | 외부 API | 판정 목적 |
| --- | --- | --- | --- | --- |
| PR Gate | PR 전 로컬 실행, CI 연결 예정 | `@SpringBootTest`, MockMvc, 고정 DB fixture | Fake 또는 Stub | API·Planner·저장 회귀 차단 |
| PostgreSQL DB Gate | PR 전 로컬 실행, CI 연결 예정 | PostgreSQL 16 Testcontainers, Flyway, JPA validate | 사용 안 함 | migration·엔티티·DB 제약 정합성 검증 |
| Provider Contract | 외부 Client 변경 시 | MockWebServer/WireMock | Stub | 응답 파싱·timeout·fallback 검증 |
| Dev Smoke E2E | 개발 서버 배포 후 | 개발 서버 HTTP, 개발 DB | 실제 ODsay·BIMS·TMAP | 배포·환경변수·DB·외부 연동 확인 |
| Performance | 주간 또는 Planner 변경 시 | 고정 fixture와 별도 실제 Provider 실행 | 실행 목적에 따라 분리 | p50·p95·호출량·품질 추세 측정 |

`ScheduleLifecycleE2ETest`는 이름과 관계없이 현재 프로세스 내부에서 MockMvc를 사용하는 PR Gate 통합 테스트로 분류한다. 배포된 서버에 실제 HTTP 요청을 보내는 테스트만 Dev Smoke E2E로 부른다.

현재 저장소에는 `pull_request` 테스트 workflow가 없고 Docker 이미지 빌드도 테스트를 제외한다. 따라서 PR·PostgreSQL Gate는 테스트 구현과 로컬 실행까지만 완료됐으며 CI 필수 체크 연결은 후속 작업이다.

## 공통 불변조건

성공 응답의 `evaluation.hardGate.passed`만 신뢰하지 않고 테스트가 응답을 기준으로 아래 항목을 직접 계산한다.

| 항목 | 검증 기준 |
| --- | --- |
| 날짜 | 요청 기간과 `days`의 날짜·일차 수가 일치 |
| 일차 조건 | 각 일차 출발지·도착지·시작시각·종료시각이 요청과 일치 |
| 필수 장소 | 모든 `mustVisitPlaceIds`가 정확히 한 번 포함 |
| 장소 중복 | 전체 일정에서 같은 장소가 중복되지 않음 |
| 시간 | 모든 도착·출발시각이 순차적이며 일차 종료시각을 초과하지 않음 |
| 경로 완결성 | 일차별 방문지 유입 경로와 마지막 도착지 경로가 존재 |
| 저장 정합성 | 생성 응답과 목록·지도·공유 응답의 장소 및 순서가 일치 |
| 품질 | Hard Gate 통과, 품질 점수와 운영 지표 필드 존재 |

## PR Gate 테스트 케이스

고정 장소 fixture와 Fake Provider를 사용한다. `test` 프로파일은 ODsay·BIMS·TMAP과 장소 적재를 강제로 비활성화한다. 테스트는 트랜잭션 롤백으로 데이터를 정리하며 실제 API Key를 요구하지 않는다.

| ID | 시나리오 | 핵심 입력 | 기대 결과 | 상태 |
| --- | --- | --- | --- | --- |
| PR-01 | 1일 일정 생성 | 필수 장소 1개, 09:00~19:00 | `201`, 공통 불변조건 통과 | 완료 |
| PR-02 | 자동 추천 보충 | 필수 장소 1개, `SYNCED` 후보 다수 | 남은 슬롯 자동 추천 | 기존 테스트 있음 |
| PR-03 | 적재 상태 필터 | `SYNCED`, `PENDING`, `FAILED` fixture 혼재 | 자동 추천에는 `SYNCED`만 포함 | 기존 테스트 있음 |
| PR-04 | 2박 3일 일차별 조건 | 날짜별 서로 다른 출발·도착지 | 각 날짜 경로가 해당 일차 조건으로 완결 | 완료 |
| PR-05 | 시간 feasibility | 장거리 경로와 짧은 가용시간 | 보정 후 종료시각 준수 또는 `400` | 기존 테스트 보강 |
| PR-06 | 잘못된 생성 요청 | 질문 누락, 중복·초과 장소, 잘못된 날짜 | `400`, 일정 미저장 | 기존 테스트 있음 |
| PR-07 | 수정과 롤백 | 장소 이동·추가·삭제 후 잘못된 수정 | 경로 재계산, 실패 시 기존 일정 보존 | 기존 테스트 있음 |
| PR-08 | 공유 생명주기 | 공유 생성→조회→지도→폐기 | 읽기 전용 조회, 폐기 후 `404` | 기존 테스트 있음 |

## Provider Contract 테스트 케이스

단위·통합 테스트에서는 실제 API를 호출하지 않는다. 원본 응답 fixture와 HTTP Stub으로 정상, 누락 필드, timeout, 오류 응답을 재현한다.

| ID | 대상 | 검증 내용 | 기대 결과 |
| --- | --- | --- | --- |
| PC-01 | ODsay | 대중교통 경로, 환승, `loadLane` 좌표 파싱 | 도보·버스·도시철도 구간과 geometry 생성 |
| PC-02 | BIMS | 도착정보 정상·빈 응답·timeout | 실시간 필드 반영 또는 명시적 미반영 |
| PC-03 | TMAP | 보행자 경로 좌표 정상·빈 geometry | 실제 보행선 우선, 실패 시 fallback 선 |
| PC-04 | 통합 fallback | Provider 4xx·5xx·timeout | 정책 범위에서 일정 완결, 실패·fallback 지표 일치 |

실제 Provider 호출은 계약 테스트와 분리한다. API Key는 환경변수로만 주입하고 호출 예산을 계산한 뒤 실행한다.

## Dev Smoke E2E 테스트 케이스

대상 URL은 `E2E_BASE_URL` 환경변수로 전달하며 현재 개발 서버 기본값은 `http://13.209.73.113`이다. 장소 검색 API는 `ingestion_status`를 반환하거나 필터링하지 않으므로 Dev Smoke에서는 적재 상태 필터 자체를 판정하지 않는다. 해당 규칙은 PR-03에서 DB fixture로 검증한다.

| ID | 시나리오 | 실행 및 검증 | 기대 결과 | 자동화 조건 |
| --- | --- | --- | --- | --- |
| DEV-01 | 적재 준비 확인 | 부산역 반경 장소 검색, 알려진 `externalContentId`와 필수 필드 확인 | 장소 목록 존재, 좌표·이미지 응답 정상 | 즉시 가능 |
| DEV-02 | 실제 1일 일정 | 검색된 장소 ID 1개를 필수 장소로 생성 | `201`, 공통 불변조건, `providers`에 `ODSAY`, 외부 HTTP 호출 존재, `FAKE` 경로 없음 | 수동 가능, 자동화 차단 |
| DEV-03 | 실제 2박 3일 일정 | 부산역→숙소→숙소→공항의 `days` 전달 | 일차별 출발·도착과 경로 완결 | 호출 예산 확인 후 실행 |
| DEV-04 | 저장·수정·지도 | DEV-02 일정 조회, 수정, 지도 조회 | 순서·체류시간 변경 후 전체 경로 재계산 | 수동 가능, 자동화 차단 |
| DEV-05 | 공유 흐름 | 공유 생성, 읽기 전용 조회, 지도, 폐기 | 폐기 후 `404 SHARE_LINK_NOT_FOUND` | 수동 가능, 자동화 차단 |
| DEV-06 | 오류 응답 | 존재하지 않는 장소 ID와 잘못된 요청 | 계약된 `400` 또는 `404`, 일정 미저장 | 즉시 가능 |

현재 공개 API에는 일정 삭제가 없다. 따라서 자동 반복 실행은 개발 DB에 테스트 일정을 누적시킨다. 일정 cleanup 또는 전용 E2E DB가 마련되기 전까지 DEV-02~DEV-05는 배포당 한 번 수동 실행하고 생성된 일정·공유 ID를 결과 문서에 기록한다. 공유 링크는 테스트 종료 시 반드시 폐기한다.

Dev Smoke 스크립트는 공유 링크 생성 직후 종료 trap을 등록하고 결과 파일의 토큰과 토큰 포함 URL을 마스킹한다. 중간 assertion 또는 네트워크 오류로 종료돼도 공유 링크 폐기를 시도한다. 일정 생성이 nginx `504`를 반환하면 기본 60초 동안 목록을 polling하며 실행 전 ID, 날짜, 일차별 endpoint, 필수 장소가 모두 일치하는 신규 일정만 timeout 후 저장 결과로 판정한다.

일정 단건 조회 API도 현재 공개되어 있지 않다. 저장 검증은 `GET /api/v1/schedules`에서 생성 ID를 찾아 비교하고 지도는 `GET /api/v1/schedules/{scheduleId}/map`을 사용한다.

## 성능 테스트

요청 내부 경로 캐시만 존재하므로 반복 요청 간 cache warm-up 개선을 기대하지 않는다. 응답의 `evaluation.operations`와 클라이언트 관측 시간을 함께 기록한다.

| ID | 실행 방식 | 표본 | 기록 지표 | 판정 |
| --- | --- | --- | --- | --- |
| PERF-01 | Fake Provider 고정 fixture | warm-up 3회 후 30회 | p50·p95, 품질 점수, Provider 호출 수 | 같은 runner로 기록한 직전 기준선 대비 20% 이상 악화 시 실패 |
| PERF-02 | 실제 Provider smoke | 동일 시나리오 3회 | 응답시간, 품질, 외부 HTTP·실패·fallback 수 | Hard Gate 실패 또는 경로 미완결 시 실패 |
| PERF-03 | 실제 Provider 주간 측정 | 호출 예산 내 20회 이상 | p50·p95, 최저·평균 품질, 호출량 | 별도 승인된 릴리스 기준 적용 |

3회 실제 Provider smoke 결과로 p95를 판정하지 않는다. 실제 Provider의 p95는 충분한 호출 예산이 확보된 PERF-03에서만 계산한다. PERF-01 runner를 처음 실행할 때 결과를 `docs/scenario-results/planner-performance-baseline.json`으로 고정하며, runner·fixture·JDK가 같은 결과끼리만 비교한다.

## 실행 순서

1. PR Gate 전체 테스트를 통과시킨다.
2. 외부 Client를 변경했다면 Provider Contract 테스트를 실행한다.
3. 개발 서버 배포 후 DEV-01로 장소 데이터 준비 상태를 확인한다.
4. 외부 API 호출 예산을 확인하고 DEV-02부터 DEV-06까지 실행한다.
5. 생성 ID, 응답시간, 품질 점수, Provider 호출 수, fallback 수를 결과 문서에 기록한다.
6. 공유 링크를 폐기하고 개발 DB의 테스트 일정 누적 여부를 기록한다.

## 자동화 우선순위

| 순서 | 작업 | 완료 기준 |
| --- | --- | --- |
| 1 | PR-04 다일 일정 MockMvc 테스트 추가 | 완료 |
| 2 | 공통 불변조건 assertion helper 작성 | 완료 |
| 3 | PostgreSQL migration Gate 추가 | 테스트 완료, CI 미연결 |
| 4 | DEV Smoke 실행 스크립트 작성 | 완료 |
| 5 | 테스트 일정 cleanup 정책 결정 | 반복 실행 후 개발 DB에 데이터가 누적되지 않음 |
| 6 | PERF-01 반복 측정 runner 작성 | p50·p95와 기준선 비교를 자동 출력 |

## 현재 구현 매핑

| 범위 | 구현 위치 | 판정 |
| --- | --- | --- |
| 생성·목록·수정·공유·지도·폐기 | `ScheduleLifecycleE2ETest` | PR Gate 일부 완료 |
| 수정 트랜잭션 롤백 | `ScheduleUpdateRollbackIntegrationTest` | 완료 |
| 요청·다일 조건 검증 | `ScheduleRequestValidatorTest`, `ScheduleServiceTest` | 완료 |
| 적재 상태별 추천 필터 | `ScheduleServiceTest.createExcludesPlacesPendingIngestion` | 완료 |
| PostgreSQL migration·JPA 정합성 | `PostgresMigrationIntegrationTest` | 테스트 완료, CI 미연결 |
| 실제 개발 서버 HTTP 흐름 | `scripts/run-dev-schedule-e2e.sh` | cleanup 전까지 쓰기 허용 수동 실행 |
| 실제 Provider 반복 성능 | 기준선 단일 실행만 존재 | runner 필요 |
