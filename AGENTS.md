# AGENTS.md

이 문서는 Codex와 기타 AI Agent가 이 프로젝트에서 작업할 때 가장 먼저 읽는 진입점이다.

## 작업 전 필수 확인

아래 문서를 순서대로 읽고 현재 코드와 비교한 뒤 작업한다.

1. `COLLABORATION.md`
2. `docs/ai-harness.md`
3. `docs/API_SPEC.md`
4. `docs/API_FIELD_GUIDE.md`
5. `docs/ERD.md`
6. `docs/api-guidelines.md`
7. `docs/spring-guidelines.md`
8. `docs/testing-guidelines.md`
9. `docs/review-checklist.md`

## 필수 규칙

- 요청 범위와 관련된 코드, 테스트, 문서를 먼저 확인한다.
- 기존 패키지 구조, 명명법, 예외 처리, 테스트 방식을 우선한다.
- `docs/API_SPEC.md`를 클라이언트와 서버 사이의 계약으로 취급한다.
- `docs/ERD.md`를 엔티티와 데이터베이스 설계의 기준으로 취급한다.
- API 경로, 메서드, 요청, 응답, 상태 코드, 오류 코드를 바꾸면 같은 작업에서 API 문서와 `docs/api-change-log.md`를 수정한다.
- 엔티티, 컬럼, 관계를 바꾸면 같은 작업에서 ERD를 수정한다.
- DTO와 Entity를 분리하고 Entity를 API 응답으로 직접 반환하지 않는다.
- 동작을 변경하면 관련 테스트를 추가하거나 수정한다.
- 테스트를 실행하지 못했다면 완료 보고에 이유를 명시한다.
- 변경 범위를 요청된 작업으로 제한한다.

## 절대 금지

- 비밀키, 토큰, 인증 헤더, 개인정보를 코드나 로그에 기록하지 않는다.
- API 명세나 ERD와 다른 구현을 설명 없이 추가하지 않는다.
- migration 도구 도입 후 공유된 기존 migration 파일을 수정하지 않는다.
- 테스트를 통과시키기 위해 테스트를 삭제하거나 약화하지 않는다.
- 관련 없는 리팩터링, 의존성 추가, 설정 변경을 함께 수행하지 않는다.
- 요청 없이 CI와 배포 설정을 수정하지 않는다.

## 완료 보고

- 변경한 기능과 파일
- 실행한 테스트와 결과
- API 또는 ERD 변경 여부
- 남은 가정, 위험, 후속 작업
