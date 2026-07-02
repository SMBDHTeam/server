# Spring Boot 개발 규칙

## 패키지와 책임

도메인 중심 패키지를 우선하며 실제 프로젝트 구조가 생기면 기존 구조를 따른다.

```text
schedule/
  controller/
  service/
  domain/
  repository/
  dto/
  client/
```

- Controller: 라우팅, DTO 바인딩, 입력 검증, 상태 코드 반환
- Service: 비즈니스 규칙, 트랜잭션, Repository와 외부 Client 조정
- Repository: 영속성 접근과 쿼리
- Client: TourAPI, ODsay, Kakao, 부산 교통 API 호출과 응답 변환

Controller에서 Repository나 외부 API를 직접 호출하지 않는다.

## DTO

- Request와 Response DTO를 분리한다.
- 외부 API DTO와 서비스 API DTO를 분리한다.
- DTO 필드명은 `docs/API_SPEC.md`와 일치시킨다.
- 입력 DTO에는 Bean Validation을 적용한다.
- Entity와 외부 원본 응답을 Controller에서 직접 반환하지 않는다.

## Service와 트랜잭션

- 상태 변경은 Service의 `@Transactional` 메서드에서 처리한다.
- 조회는 필요할 때 `@Transactional(readOnly = true)`를 사용한다.
- 외부 API 호출을 포함한 긴 트랜잭션을 피한다.
- 일정 생성은 외부 데이터 수집·계산과 내부 저장 단계를 구분한다.
- 일정 수정 후 영향을 받는 대중교통 경로를 재계산한다.

## Entity와 Repository

- Entity는 `docs/ERD.md`의 테이블과 관계를 따른다.
- 양방향 관계는 필요성이 명확할 때만 사용한다.
- 컬렉션은 외부에 직접 노출하지 않는다.
- 관계는 기본적으로 지연 로딩을 검토하고 조회 시 N+1을 확인한다.
- `source + external_content_id`, 일정별 `day_no`, 날짜별 방문 순서 등 명세의 고유 조건을 DB 제약과 함께 검토한다.

## 예외

예외는 중앙 처리한다.

```text
GlobalExceptionHandler
ErrorCode
ErrorResponse
BusinessException
```

- 외부 Provider 오류를 공개 응답에 그대로 노출하지 않는다.
- 입력 오류와 비즈니스 오류를 구분한다.
- 명세에 정의된 HTTP 상태와 오류 코드를 반환한다.

## 설정과 로그

- 묶음 설정은 `@ConfigurationProperties`를 우선한다.
- 환경별 값과 API Key를 하드코딩하지 않는다.
- 1차 스프린트에서는 `SecurityFilterChain`에 명세 API의 익명 접근 정책을 명시한다.
- 인증을 사용하지 않더라도 Spring Security 기본 설정이 API를 차단하지 않는지 Controller 테스트로 확인한다.
- 요청 파라미터에 API Key가 포함된 전체 URL을 로그로 남기지 않는다.
- 일정 ID와 trace ID 등 문제 추적에 필요한 값만 안전하게 기록한다.

## 의존성

- 기존 Spring 지원 라이브러리를 우선한다.
- 새 의존성은 실제 필요성과 유지보수 상태를 확인한다.
- 단순 유틸리티 때문에 큰 의존성을 추가하지 않는다.
