# 인증·인가와 관리자 기능 설계

작성 2026-08-24. 이 문서는 기획·설계 기준이며, 구현이 끝나면 `API_SPEC.md`·`ERD.md`·
`api-change-log.md`가 단일 기준이 된다.

## 1. 결정 사항

| 항목 | 결정 | 이유 |
| --- | --- | --- |
| 인증 방식 | 구글 ID 토큰 검증 후 **자체 JWT 발급** | 구글 의존이 로그인 시점 1회로 끝난다. `role`·정지 상태를 토큰에 담아 매 요청 DB 조회를 피한다 |
| 리프레시 토큰 | **Redis** | 만료 자동 처리, 로그아웃·강제 만료가 즉시 반영된다 |
| 일정 소유권 | **`schedules.user_id` 추가** | 없으면 내 일정 목록·수정 권한·공유가 성립하지 않는다. FastAPI 동시 변경 필요 |
| 관리자 범위 | 신고 처리, 사용자 관리, 장소 데이터 관리, 통계 | 아래 5절 |

## 2. 현재 상태

설계의 출발점이다. 전부 확인한 사실이다.

- `SecurityConfig`가 `/api/v1/**`를 포함해 **전부 `permitAll()`**이다. `spring-boot-starter-security`는
  의존성에 있으나 사실상 꺼져 있다. JWT 라이브러리는 없다.
- `users`(V6)에 `id`, `nickname`, `profile_image_url`, `created_at`, `deleted_at`만 있다.
  **`email`·`provider`·`provider_id`·`role`·`status`가 없다.**
- `schedules`에 **`user_id`가 없다.** 일정에 주인이 아예 없어 `GET /schedules`가 전체 사용자의
  일정을 반환한다. 이 테이블은 **FastAPI가 쓴다.**
- CORS 허용 헤더가 `SecurityConfig`에 하드코딩돼 있다.
  `["Content-Type", "X-Trace-Id", "Idempotency-Key"]` — **`Authorization`이 없다.**
  같은 이유로 PR #78의 `X-User-Id`도 브라우저 preflight에서 막힌다.
- 커뮤니티(PR #78)는 작성자를 `X-User-Id` 헤더로 받는다. 인증 도입 시 전면 제거 대상이다.

## 3. 인증 흐름

```text
프론트                          Spring                         구글
  │  구글 로그인                                                  │
  │ ─────────────────────────────────────────────────────────────>│
  │  <──────────────────────────── ID 토큰(JWT) ───────────────────│
  │                                  │                            │
  │ POST /api/v1/auth/google        │                            │
  │   { "idToken": "..." }          │                            │
  │ ───────────────────────────────>│  JWKS 로 서명 검증           │
  │                                  │ ──────────────────────────>│
  │                                  │  iss·aud·exp 확인           │
  │                                  │  users upsert (sub 기준)    │
  │                                  │  액세스 JWT + 리프레시 발급  │
  │  <─────────── { accessToken, refreshToken, user } ────────────│
  │                                  │
  │  이후 모든 요청: Authorization: Bearer <accessToken>
```

**구글 ID 토큰 검증 시 확인할 것**

- 서명: `https://www.googleapis.com/oauth2/v3/certs` (JWKS, 캐시하고 주기적으로 갱신)
- `iss`: `https://accounts.google.com` 또는 `accounts.google.com`
- `aud`: 우리 클라이언트 ID와 일치. **이걸 빼면 다른 앱의 토큰으로 로그인된다.**
- `exp`: 만료 확인
- `email_verified`: `true`인 경우만 허용

`sub`(구글 고유 ID)를 `provider_id`로 쓴다. **이메일을 식별자로 쓰지 않는다.**
이메일은 바뀔 수 있고 재사용될 수 있다.

### 토큰 수명

| 토큰 | 수명 | 저장 위치 |
| --- | --- | --- |
| 액세스 | 30분 | 저장하지 않음(무상태 검증) |
| 리프레시 | 14일 | Redis `refresh:{userId}:{tokenId}` |

리프레시는 **회전(rotation)**시킨다. 갱신할 때마다 새 값을 발급하고 이전 값을 지운다.
이미 쓴 리프레시가 다시 들어오면 탈취로 보고 해당 사용자의 리프레시를 전부 폐기한다.

### 액세스 토큰 클레임

```json
{
  "sub": "42",
  "role": "USER",
  "iat": 1756000000,
  "exp": 1756001800
}
```

`role`을 담아 매 요청 DB 조회를 피한다. 대신 **권한 변경이 최대 30분 늦게 반영된다.**
정지·강등을 즉시 반영해야 하면 Redis에 `revoked:{userId}` 키를 두고 필터에서 확인한다.
1차에서는 리프레시 폐기만으로 충분하다고 본다.

## 4. 스키마 변경

### V9 — users 확장

```sql
ALTER TABLE users ADD COLUMN IF NOT EXISTS email varchar(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider varchar(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider_id varchar(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS role varchar(20) NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD COLUMN IF NOT EXISTS status varchar(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN IF NOT EXISTS suspended_until timestamp;

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_provider
    ON users (provider, provider_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_users_role ON users (role);
```

`provider`·`provider_id`·`email`은 **nullable로 둔다.** 기존 행이 있으면 `NOT NULL`이 실패한다.
로그인으로 만들어지는 행은 애플리케이션이 항상 채운다.

`role`은 `USER` / `ADMIN`. `status`는 `ACTIVE` / `SUSPENDED` / `WITHDRAWN`.

### V10 — schedules 소유자

```sql
ALTER TABLE schedules ADD COLUMN IF NOT EXISTS user_id bigint REFERENCES users(id);
CREATE INDEX IF NOT EXISTS idx_schedules_user_id ON schedules (user_id);
```

**nullable이어야 한다.** 기존 일정 50여 건은 소유자를 알 수 없어 `NULL`로 남는다.
`NULL`인 일정은 목록에서 아무에게도 보이지 않고, 단건 조회는 공유 링크로만 접근하게 둔다.

### V11 — 장소 숨김 (관리자용)

```sql
ALTER TABLE places ADD COLUMN IF NOT EXISTS hidden_at timestamp;
ALTER TABLE places ADD COLUMN IF NOT EXISTS hidden_reason text;
```

잘못 적재된 장소를 지우면 TourAPI 동기화가 다시 살려낸다. 숨김 표시로 검색·일정 후보에서
제외한다.

**주의.** `schedules`와 `places`는 FastAPI도 읽고 쓴다. 컬럼을 바꾸면 data 저장소에 알려야
한다. Flyway 기준은 계속 Spring이다.

## 5. 인가 정책

### 경로별 접근 수준

| 수준 | 경로 | 비고 |
| --- | --- | --- |
| 공개 | `/api/v1/auth/**` | 로그인·갱신 |
| 공개 | `/api/v1/trip-questions`, `/api/v1/places/**`, `/api/v1/locations/**` | 검색은 로그인 없이 |
| 공개 | `/api/v1/shared-schedules/**` | 공유 링크는 비로그인 열람이 목적 |
| 공개 | `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health` | dev 한정 |
| 선택적 | `GET /api/v1/posts`, `/api/v1/posts/{id}`, `/api/v1/users/{id}` | 토큰이 있으면 `liked`·`bookmarked`·`following`을 채운다 |
| 필수 | 일정 생성·목록·단건·수정·지도, 공유 링크 발급·폐기 | |
| 필수 | 커뮤니티 쓰기 전부, 프로필 변경, 북마크·팔로우·차단·신고 | |
| 관리자 | `/api/v1/admin/**` | `hasRole('ADMIN')` |

**선택적 인증**이 핵심이다. 비로그인도 피드를 볼 수 있어야 하고, 로그인했으면 좋아요 여부가
표시돼야 한다. Spring Security의 `anonymous()`를 허용하고 컨트롤러에서 `null` 가능한
`userId`를 받는다. 지금 `X-User-Id`를 `required = false`로 둔 자리와 정확히 대응한다.

### 소유권 검증

인가는 두 층이다.

1. **경로 수준** — 로그인했는가, 관리자인가. `SecurityConfig`가 처리한다.
2. **자원 수준** — 내 것인가. 서비스가 처리한다.

자원 수준은 이미 커뮤니티에 `POST_ACCESS_DENIED`·`COMMENT_ACCESS_DENIED`로 있다.
일정에도 같은 것이 필요하다.

```java
if (!schedule.isOwnedBy(userId)) {
    throw new BusinessException(ErrorCode.SCHEDULE_ACCESS_DENIED);
}
```

**`GET /schedules`는 목록 자체를 사용자로 좁힌다.** 필터가 아니라 쿼리 조건이어야 한다.
FastAPI에 `userId`를 넘겨 그쪽에서 걸러야 하며, Spring이 전체를 받아 거르는 방식은
페이징과 함께 깨진다.

### 정지된 사용자

`status = SUSPENDED`이고 `suspended_until`이 미래면 쓰기 요청을 거부한다. 읽기는 허용한다.
액세스 토큰에 `role`만 담으므로 정지는 **리프레시 폐기 + 필터에서 상태 확인**으로 처리한다.
정지 확인은 쓰기 경로에서만 하면 되므로 매 요청 DB 조회가 아니다.

## 6. 관리자 기능

`/api/v1/admin` 아래에 둔다. 별도 컨트롤러 패키지 `admin/`.

### 6.1 신고 처리

`reports` 테이블과 `ReportStatus`가 PR #78에 이미 있다. 연결만 하면 된다.

| Method | Endpoint | 설명 |
| --- | --- | --- |
| `GET` | `/admin/reports` | 상태·대상 유형·기간 필터, 페이징 |
| `GET` | `/admin/reports/{id}` | 신고 상세와 대상 원본 |
| `PATCH` | `/admin/reports/{id}` | 상태 변경 (`REVIEWING`·`RESOLVED`·`REJECTED`) |
| `DELETE` | `/admin/posts/{postId}` | 관리자 권한 게시물 삭제 |
| `DELETE` | `/admin/comments/{commentId}` | 관리자 권한 댓글 삭제 |

관리자 삭제도 소프트 삭제다. 처리 이력을 남기려면 `reports`에 `handled_by`·`handled_at`이
필요하다. V9에 함께 넣는다.

### 6.2 사용자 관리

| Method | Endpoint | 설명 |
| --- | --- | --- |
| `GET` | `/admin/users` | 닉네임·이메일 검색, `status` 필터, 페이징 |
| `GET` | `/admin/users/{id}` | 상세. 게시물·신고 이력 요약 |
| `PATCH` | `/admin/users/{id}/status` | 정지·해제. 기간과 사유 |

정지 시 해당 사용자의 리프레시 토큰을 Redis에서 전부 지운다.

**관리자를 누가 만드는가.** DB에서 직접 바꾼다.

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';
```

로그인은 권한을 건드리지 않는다. 새로 만들어지는 사용자는 항상 `USER`이고, 이미 있는
사용자의 `role`은 로그인 시 손대지 않는다. 로그인이 권한을 덮어쓰면 DB로 준 권한이
다음 로그인에 사라진다.

대상 사용자가 **한 번은 로그인했어야 한다.** 행이 있어야 바꿀 수 있다.

관리자가 다른 관리자를 임명하는 API는 1차 범위에서 제외한다. 권한 상승 경로가 늘수록
위험하고, 운영자가 DB를 만질 수 있는 규모에서는 필요하지 않다.

### 6.3 장소 데이터 관리

| Method | Endpoint | 설명 |
| --- | --- | --- |
| `GET` | `/admin/places/ingestion` | 적재 상태 요약. `ingestion_status`별 건수, 마지막 실행 시각 |
| `POST` | `/admin/places/ingestion` | 수동 적재 트리거. 스케줄러와 같은 작업 |
| `PATCH` | `/admin/places/{placeId}/hidden` | 숨김·해제와 사유 |

수동 적재는 **TourAPI 일일 예산 900회**를 공유한다. 실행 전 남은 예산을 응답에 보여주고,
초과가 예상되면 거부한다.

### 6.4 통계 대시보드

| Method | Endpoint | 설명 |
| --- | --- | --- |
| `GET` | `/admin/stats/summary` | 가입자·게시물·일정 총계와 기간 증감 |
| `GET` | `/admin/stats/trend?metric=&from=&to=` | 일자별 추이 |
| `GET` | `/admin/stats/popular?type=place\|hashtag` | 인기 장소·해시태그 |

조회 전용이라 위험은 낮다. 다만 집계 쿼리가 전체 스캔이 되기 쉬우므로
`created_at` 인덱스를 확인하고, 무거워지면 일 단위 집계 테이블을 별도로 둔다.
1차는 실시간 집계로 시작한다.

## 7. 함께 고쳐야 하는 것

인증과 직접 관계는 없지만 같이 처리해야 동작한다.

- **CORS 허용 헤더에 `Authorization` 추가.** 지금 하드코딩이라 설정으로 못 바꾼다.
  `CorsProperties`로 옮기고 `Authorization`·`X-User-Id`를 넣는다. **이걸 빼면 브라우저에서
  로그인 자체가 안 된다.**
- **`ErrorResponseOpenApiConfig`에 401·403 추가.** 지금 400·404·409·410·500·503만 붙인다.
- **Swagger에 `bearerAuth` 보안 스키마 추가.** 없으면 Swagger UI에서 인증 API를 시험할 수 없다.
- **`ErrorCode` 신설** — `UNAUTHORIZED`(401), `INVALID_TOKEN`(401), `TOKEN_EXPIRED`(401),
  `FORBIDDEN`(403), `SCHEDULE_ACCESS_DENIED`(403), `USER_SUSPENDED`(403),
  `INVALID_GOOGLE_TOKEN`(401).
- **`GlobalExceptionHandler`에 인증 예외 처리.** Spring Security 예외는 필터에서 나므로
  `@ControllerAdvice`가 잡지 못한다. `AuthenticationEntryPoint`와 `AccessDeniedHandler`를
  직접 구현해 같은 `ErrorResponse` 형태로 맞춘다. **이걸 놓치면 401·403만 응답 형태가 달라진다.**
- **커뮤니티 `X-User-Id` 제거.** PR #78 머지 후 31개 엔드포인트에서 걷어낸다.

## 8. 인프라

Redis를 새로 띄운다.

- 로컬: `docker-compose.local.yml`에 `redis:7-alpine` 추가
- dev: `hackathon-network`에 `redis` 컨테이너 추가, `deploy-dev.yml`에 기동 단계 추가
- 설정: `REDIS_HOST`, `REDIS_PORT`. **비밀번호는 `.env.server`에만 둔다.**
- 의존성: `spring-boot-starter-data-redis`

새 환경변수는 다음과 같다. 값은 `.env.server`에만 둔다.

| 변수 | 용도 |
| --- | --- |
| `GOOGLE_CLIENT_ID` | ID 토큰 `aud` 검증 |
| `JWT_SECRET` | 액세스 토큰 서명. 최소 256비트 |
| `JWT_ACCESS_TTL` | 기본 `30m` |
| `JWT_REFRESH_TTL` | 기본 `14d` |
| `REDIS_HOST`, `REDIS_PORT` | 리프레시 토큰 저장소 |

## 9. 배포 순서 — 인가는 마지막에

인가(경로별 접근 제한)를 켜는 순간이 유일한 파괴적 변경이다. 비로그인 요청이 401을 받기
시작하므로 프론트가 준비되기 전에 올리면 서비스가 멈춘다.

그래서 **그 전까지는 전부 공개로 두고 기능만 쌓는다.** 로그인·토큰·소유자 기록·관리자 기능을
먼저 완성해 두면, 마지막에 스위치 하나만 켜면 된다.

```text
1. CORS 헤더 수정                  기존 동작 그대로. PR #78 도 이게 있어야 브라우저에서 동작
2. Redis + V9 users 확장           스키마만. 쓰는 곳 없음
3. 구글 로그인 + JWT 발급           토큰을 주지만 아무 데도 요구하지 않는다
4. V10 schedules.user_id (nullable) 컬럼만. 아직 미사용
5. FastAPI 가 user_id 채우기        data 저장소. 토큰이 있으면 소유자를 기록한다
6. 관리자 기능                      ★ 여기만 처음부터 인가를 건다 (아래 참고)
   ─────────────────────────────────────────────────────────────
7. 경로별 인가 적용                 ★ 파괴적. 프론트 배포와 동시에
8. GET /schedules 사용자 스코프      7 이후
9. 커뮤니티 X-User-Id 제거          7 이후
```

1~6은 **어떤 클라이언트도 깨뜨리지 않는다.** 토큰을 보내면 소유자가 기록되고, 안 보내도
지금처럼 동작한다. 7번에서 프론트와 맞춰 한 번에 전환한다.

### 다만 관리자 API 는 예외다

인가를 미루는 것은 **사용자 API 에만** 해당한다. `/api/v1/admin/**` 은 만드는 순간부터
`hasRole('ADMIN')` 을 걸어야 한다.

`SecurityConfig` 가 지금 `/api/v1/**` 를 통째로 `permitAll()` 하므로, 관리자 컨트롤러를
그대로 추가하면 **누구나 사용자를 정지시키고 게시물을 지울 수 있다.** 6번을 시작하기 전에
최소한 다음 한 줄이 먼저 들어가야 한다.

```java
.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
.requestMatchers("/api/v1/**").permitAll()
```

즉 **3번(로그인·JWT)이 6번보다 반드시 먼저**다. 토큰에 `role` 이 없으면 관리자를 구분할
방법이 없다.

## 10. 작업 분해

| # | 작업 | 규모 | 파괴적 | 비고 |
| --- | --- | --- | --- | --- |
| 1 | CORS 헤더 (`Authorization`, `X-User-Id`) | 30분 | 아니오 | 지금 바로. PR #78 도 이게 필요 |
| 2 | Redis 추가 (로컬·dev·배포) | 반나절 | 아니오 | |
| 3 | V9 마이그레이션, `User` 확장, ERD | 반나절 | 아니오 | |
| 4 | 구글 ID 토큰 검증 + `POST /auth/google` | 1일 | 아니오 | JWKS 캐시, `aud` 검증 |
| 5 | JWT 필터·리프레시 회전·`/auth/refresh`·`/auth/logout` | 1.5일 | 아니오 | 토큰을 요구하지는 않는다 |
| 6 | 401·403 응답 형태 통일, `admin/**` 만 인가 | 1일 | 아니오 | `AuthenticationEntryPoint`·`AccessDeniedHandler` |
| 7 | V10 + FastAPI `user_id` 저장 (**두 저장소**) | 1일 | 아니오 | 순서 주의 |
| 8 | 관리자 — 신고 처리 | 1일 | 아니오 | `reports`·`ReportStatus` 재사용 |
| 9 | 관리자 — 사용자 관리 (정지·해제) | 1일 | 아니오 | 리프레시 폐기 연동 |
| 10 | 관리자 — 장소 데이터 (V11 숨김, 적재 트리거) | 1일 | 아니오 | TourAPI 예산 확인 |
| 11 | 관리자 — 통계 | 1일 | 아니오 | 실시간 집계로 시작 |
| 12 | **경로별 인가 적용** | 1일 | **예** | 프론트 배포와 동시 |
| 13 | `GET /schedules` 사용자 스코프 + 페이징 | 반나절 | 예 | 12 이후 |
| 14 | 커뮤니티 `X-User-Id` 제거 | 1일 | 예 | 12 이후, PR #78 머지 후 |

합계 대략 **12일**. 1~11 이 **11일이고 전부 무해하다.** 파괴적 변경은 12~14 뿐이며
2.5일이다. 프론트 일정에 맞춰 이 구간만 조율하면 된다.

## 11. 남은 결정

- **탈퇴 처리.** 소프트 삭제만 할지, 게시물·댓글까지 어떻게 할지. 커뮤니티는 작성자를
  `users`에 FK로 걸어 두어 물리 삭제가 불가능하다.
- **`user_id`가 `NULL`인 기존 일정 50여 건.** 방치할지, 정리할지.
- **관리자 화면.** 백엔드 API만 낼지, 별도 프론트가 붙을지. 붙는다면 CORS 오리진 추가가 필요하다.
- **감사 로그.** 관리자의 삭제·정지 행위를 기록할지. 지금 설계에는 `reports.handled_by`만 있다.
