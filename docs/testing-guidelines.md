# 테스트 규칙

## 기본 원칙

- 기능 추가와 동작 변경에는 테스트를 작성한다.
- 버그 수정에는 가능한 한 회귀 테스트를 작성한다.
- 구현 세부사항보다 외부에서 관찰 가능한 동작을 검증한다.
- 테스트를 통과시키기 위해 기존 테스트를 삭제하거나 약화하지 않는다.

## 최소 테스트 범위

| 대상 | 확인 내용 |
| --- | --- |
| Controller | 요청 검증, 상태 코드, JSON 구조, 오류 응답 |
| Service | 일정 생성·수정 규칙, 필수 방문 장소 포함, 경로 재계산 |
| Repository | 커스텀 쿼리, 관계 매핑, 고유 조건 |
| External Client | 정상 응답 변환, 타임아웃, 빈 응답, Provider 오류 |

## API 변경 시

- 정상 요청과 응답
- 필수값 누락과 잘못된 값
- 존재하지 않는 일정 또는 장소
- 경로를 찾지 못한 경우
- 외부 Provider 장애
- `docs/API_SPEC.md`와 동일한 JSON 필드

## 외부 API 테스트

- 단위 테스트에서는 실제 API를 호출하지 않고 Stub 또는 Mock을 사용한다.
- 실제 호출 확인은 별도 수동 또는 통합 테스트로 구분한다.
- API Key를 테스트 코드와 Fixture에 넣지 않는다.
- 원본 응답 Fixture에는 필요한 필드와 누락 가능 필드를 함께 포함한다.

## 권장 도구

- Service: JUnit 5, Mockito
- Controller: MockMvc
- Repository: `@DataJpaTest`
- 전체 흐름: 필요한 경우 `@SpringBootTest`
- 외부 HTTP: 필요한 경우 WireMock 또는 MockWebServer

Testcontainers는 실제 DB 고유 동작을 확인해야 할 때 도입하며 모든 테스트의 기본 요구사항은 아니다.

## 실행

```bash
./gradlew test
```

전체 빌드가 필요한 경우:

```bash
./gradlew clean build
```

실행하지 못한 검증은 완료 보고와 PR에 이유를 기록한다.
