# 즉흥여행 Preview·일정 저장 연동

## 클라이언트 호출 순서

1. Bearer 토큰으로 `POST /api/v1/spontaneous-trips/course`를 호출한다.
2. 응답의 `previewId`, `previewToken`, `previewExpiresAt`을 메모리에 보관하고 코스를 표시한다.
3. 사용자가 저장을 누를 때 새 `Idempotency-Key`를 한 번 생성한다.
4. 같은 키와 원본 `previewId`, `previewToken`으로
   `POST /api/v1/spontaneous-trips/schedules`를 호출한다.
5. 네트워크 오류나 응답 유실 때는 세 값을 바꾸지 않고 재시도한다.
6. `201`의 공통 `ScheduleResponse.id`로 기존 일정 상세·지도 화면을 연다.

저장하지 않고 코스를 다시 생성하면 새 Preview이므로 새 키를 사용한다. 토큰을 JSON으로
해석하거나 응답 코스를 편집해 토큰과 별도로 저장 요청에 보내지 않는다.

## 응답 사용 규칙

- 일정 목록·상세에서 `scheduleType`으로 `PLANNED`와 `SPONTANEOUS`를 구분한다.
- 즉흥 일정의 자정 경계는 stop/transit `arriveAtDateTime`, `departAtDateTime`으로 표시한다.
  기존 `arriveAt`, `departAt`만 조합해 날짜를 추측하지 않는다.
- 저장된 즉흥 일정의 전체 날짜시간은 목록·상세·PATCH 응답에서 `Asia/Seoul`(+09:00)로
  정규화된다. ISO-8601 문자열의 날짜와 offset을 함께 파싱한다.
- `finalTransit`은 마지막 방문지에서 복귀 위치까지의 구간이다.
- 지도는 공통 `/api/v1/schedules/{scheduleId}/map` 응답을 사용한다.
- `fareAmount`, 역 이름, 지도선 등이 `null`/빈 배열이면 Provider가 확인하지 못한 사실이다.
  클라이언트가 임의 값을 만들어 표시하지 않는다.

## 오류 처리

| HTTP/코드 | 클라이언트 처리 |
| --- | --- |
| `400 SPONTANEOUS_PREVIEW_INVALID` | 토큰을 버리고 코스를 다시 생성 |
| `403 SPONTANEOUS_PREVIEW_OWNER_MISMATCH` | 현재 로그인 사용자가 바뀌었음을 안내 |
| `409 IDEMPOTENCY_KEY_REUSED` | 새 키로 같은 요청을 반복하지 말고 요청 상태 확인 |
| `409 SPONTANEOUS_PREVIEW_ALREADY_SAVED` | 오류 응답의 `scheduleId`로 기존 저장 결과 상세로 이동 |
| `410 SPONTANEOUS_PREVIEW_EXPIRED` | 코스를 다시 생성 |
| `422 SPONTANEOUS_PLACE_HIDDEN` | 코스를 다시 생성 |
| `422 SPONTANEOUS_RETURN_TIME_EXCEEDED` | 수정 전 상태를 유지하고 시간/방문지를 조정 |

## 서버·데이터 배포 설정

Spring은 외부 클라이언트가 보낸 사용자 ID 헤더를 신뢰하지 않는다. 액세스 토큰에서 읽은
사용자 ID만 내부 `X-Auth-User-Id`로 data 서비스에 전달한다. 브라우저 CORS에는
`Authorization`, `Idempotency-Key`가 허용되어야 한다.

모든 data 인스턴스에 같은 32바이트 이상의 고엔트로피 `SPONTANEOUS_PREVIEW_SECRET`을
설정한다. 기본 토큰
수명은 20분이며 필요하면 `SPONTANEOUS_PREVIEW_TTL_SECONDS`로 조정한다. 키를 바꾸면 기존
미저장 Preview는 모두 무효가 되므로 배포 중 인스턴스별 키가 달라지지 않게 한다.

DB에는 서버 저장소의 Flyway `V18__spontaneous_schedule_integration.sql`을 먼저 적용한다.
data 서비스는 기존 `SPRING_DATASOURCE_*` 연결을 사용하며 별도 저장소나 큐가 필요 없다.

## 구현 불변조건

- `/course` 성공 경로에서 비즈니스 DB write 금지
- `/schedules` 저장 경로에서 Planner·외부 Provider 재호출 금지
- 장소 이름 기반 병합 금지
- 일정·장소·경로·멱등성 완료는 단일 트랜잭션
- DB 커밋 전 성공 응답 금지
- 로그에 `previewToken`, API 키, 인증 헤더 출력 금지
