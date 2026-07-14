# AI 개발 운영 기준

이 문서는 Codex와 Claude Code를 포함한 AI Agent가 빠르게 코드를 작성하더라도 프로젝트 계약과 품질을 유지하기 위한 공통 기준이다.

## 기준 문서

| 문서 | 역할 |
| --- | --- |
| `AGENTS.md` | Codex와 공통 AI Agent 진입점 |
| `CLAUDE.md` | Claude Code 진입점 |
| `COLLABORATION.md` | 브랜치, 작업 전후 절차, 금지사항 |
| `docs/API_SPEC.md` | API 요청·응답 계약 |
| `docs/API_FIELD_GUIDE.md` | API 필드의 자료형과 의미 |
| `docs/ERD.md` | 테이블, 컬럼, 관계의 기준 |
| `docs/api-change-log.md` | API 계약 변경 이력 |
| `docs/planner-performance-baseline.md` | 일정 Planner Hard Gate, 품질 점수, Iteration 기준선 |
| `docs/examples/planner-console/README.md` | 일정 생성 API 로컬 E2E 콘솔 실행과 검증 절차 |
| `docs/infra.md` | 로컬 PostgreSQL, Swagger UI와 환경별 실행 절차 |

서로 충돌할 경우 실제 기능 계약은 `docs/API_SPEC.md`, 데이터 설계는 `docs/ERD.md`를 우선한다. 문서가 잘못되었거나 불완전하면 임의로 구현하지 말고 문서와 변경 이력을 함께 수정한다.

## 작업 흐름

### 작업 전

- 요청된 동작과 완료 조건을 확인한다.
- 관련 API, ERD, 코드, 테스트를 읽는다.
- API 계약, DB, 외부 API, 보안에 미치는 영향을 판단한다.
- 애매한 부분은 가장 작은 합리적 가정을 사용하고 완료 보고에 남긴다.
- 데이터 손실, 비밀값, 공개 API 호환성에 영향을 주는 애매함은 확인 없이 진행하지 않는다.

### 구현 중

- 기존 프로젝트 구조와 명명법을 우선한다.
- Controller, Service, Repository의 책임을 분리한다.
- 요청·응답 DTO, 외부 API DTO, Entity를 분리한다.
- API와 ERD에 없는 필드나 관계를 편의상 추가하지 않는다.
- 외부 API 장애가 내부 예외나 스택 트레이스로 노출되지 않게 변환한다.
- 변경 범위를 현재 작업에 필요한 부분으로 제한한다.

### 작업 후

- 관련 테스트와 빌드를 실행한다.
- API 변경 시 API 명세, 컬럼 설명, 변경 이력을 함께 수정한다.
- DB 변경 시 ERD를 수정한다. migration 도구 도입 후에는 새 migration을 함께 추가한다.
- 문서 링크와 예시 JSON이 실제 계약과 일치하는지 확인한다.
- 변경 내용, 검증 결과, 문서 변경 여부, 남은 위험을 보고한다.

## 프로젝트 고정 계약

- Base Path는 `/api/v1`이다.
- 1차 스프린트에는 인증과 사용자 도메인이 없다.
- `GET /api/v1/schedules`는 파라미터 없이 전체 일정을 반환한다.
- 일정 수정은 편집 토큰과 `version` 없이 즉시 반영한다.
- 일정 생성의 `mustVisitPlaceIds`는 내부 `places.id` 목록이다.
- 지정된 필수 방문 장소는 생성된 `schedule_stops`에 포함해야 한다.
- 질문과 답변 ID는 의미가 고정된 문자열 ID를 사용한다.
- 장소 검색과 교체는 내부 DB, 출발지·도착지는 Kakao Local 검색을 사용한다.
- 주변 편의시설은 실시간 응답이며 DB에 적재하지 않는다.
- 대중교통 경로는 외부 Provider 응답을 내부 경로 모델로 변환하여 저장한다.

## 보안

- API Key, 토큰, 인증 헤더, 개인정보를 저장소와 로그에 남기지 않는다.
- 환경별 설정은 환경 변수 또는 승인된 설정 방식을 사용한다.
- 공개 오류 응답에 스택 트레이스, SQL, 내부 클래스명을 포함하지 않는다.
- 공유 토큰 원문은 DB에 저장하지 않고 해시를 저장한다.

## 데이터베이스

- Entity는 API 계약이 아니라 영속성 모델이다.
- 관계 추가 시 N+1과 지연 로딩을 검토한다.
- migration 도구 도입 후 공유된 migration은 수정하지 않고 새 migration을 추가한다.
- `raw_json`은 외부 원본 확인용이며 API 응답으로 직접 노출하지 않는다.

## 자동화 범위

이 문서는 개발자와 AI Agent가 따라야 할 작업 규칙을 정의한다. CI, Git Hook, 별도 검증 스크립트 도입은 현재 범위에 포함하지 않는다.
