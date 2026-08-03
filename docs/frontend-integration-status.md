# 프론트엔드 연동 현황

프론트 저장소 `tour-client`(브랜치 `feat/schedule-creation-page-api-integration`)와 이 서버의 연동 상태를
2026-08-03 기준으로 정리한다. 실제 서버를 띄우고 프론트가 호출하는 순서대로 검증한 결과를 담았다.

## 1. 연결 구성

```text
브라우저
  └─ Next.js (tour-client)
       ├─ /api/locations/search   → Naver 지역검색      (프론트 자체 라우트)
       ├─ /api/places/images      → Naver 이미지검색    (프론트 자체 라우트)
       └─ /api/v1/*               → rewrite → BACKEND_API_URL → 이 서버
```

| 설정 | 값 | 위치 |
| --- | --- | --- |
| `NEXT_PUBLIC_API_BASE_URL` | `/api/v1` | 프론트 `.env.local` |
| `BACKEND_API_URL` | `http://localhost:18080` | 프론트 `.env.local`, `next.config.ts`의 rewrite 대상 |
| `NEXT_PUBLIC_SCHEDULE_V2_MODE` | `live` | `disabled`면 호출 자체를 막고, `mock`이면 서버 대신 목 데이터를 쓴다 |

**서버를 18080으로 띄워야 프론트가 붙는다.** 기본 실행은 8080이므로 포트를 맞춰야 한다.

```bash
SERVER_PORT=18080 scripts/run-local-live-transit.sh
```

## 2. 화면 흐름과 호출 API

```text
/trips/new/date         날짜·출발지          GET  /locations/search   (실패 시 프론트 Naver 라우트)
      ↓
/trips/new/step1        동행·이동            GET  /trip-questions      (uiStep=1)
      ↓
/trips/new/step2        속도·환승            (질문 API 없이 화면에서 직접 답변 세팅)
      ↓
/trips/new/step3        테마                 GET  /trip-questions      (uiStep=3)
      ↓
/trips/new/places/search 가고싶은 장소        GET  /places?scope=ALL
                                            POST /places/resolve
      ↓
/trips/new/preview      조건 확인            POST /schedule-previews
                                            GET  /schedule-previews/{previewId}
      ↓
/trips/new/generating   생성 중              POST /schedules  + Idempotency-Key
      ↓
/trips/{scheduleId}     결과                 GET  /schedules/{id}
                                            GET  /schedules/{id}/map?dayNo=
```

호출을 담당하는 파일은 프론트의 `src/lib/api/` 아래에 있다.
`client.ts`가 공통 fetch와 `ApiError`를 담당하고, 도메인별로 `questions.ts`, `locations.ts`,
`places.ts`, `schedule-previews.ts`, `schedules.ts`로 나뉜다.

### 질문 단계 매핑

`GET /trip-questions`의 `uiStep`으로 화면이 갈린다. 5개 모두 `required=true`이므로 하나라도 빠지면
Preview 생성이 400이 된다.

| uiStep | 질문 | 화면 | 선택 개수 |
| --- | --- | --- | --- |
| 1 | `COMPANION`, `MOBILITY` | `/trips/new/step1` | 각 1개 |
| 2 | `PACE`, `TRANSIT` | `/trips/new/step2` | 각 1개 |
| 3 | `THEME` | `/trips/new/step3` | 1~3개 |

step1·step3은 공용 컴포넌트 `QuestionStepPage`가 서버 질문을 그려주고 `minSelections`를 만족해야
다음 버튼이 열린다. step2는 서버 질문을 쓰지 않고 화면에서 `PACE`/`TRANSIT` 답변 ID를 직접 넣는다.
**질문이나 답변 ID가 서버에서 바뀌면 step2는 자동으로 따라오지 않는다.**

입력값은 `sessionStorage`에 저장된다(`src/store/trip-draft.tsx`). 탭을 닫으면 사라진다.

## 3. 검증 결과 (2026-08-03)

서버를 18080으로 띄우고 프론트와 동일한 파라미터로 순서대로 호출했다.

| # | 호출 | 결과 |
| --- | --- | --- |
| ① | `GET /trip-questions` | 200 |
| ② | `GET /locations/search?keyword=부산역&size=10` | 200 |
| ③ | `GET /places?keyword=..&scope=ALL&size=20` | 200 (아래 이슈 수정 후) |
| ④ | `POST /places/resolve` | 200 |
| ⑤ | `POST /schedule-previews` | 201 |
| ⑥ | `GET /schedule-previews/{previewId}` | 200 |
| ⑦ | `POST /schedules` + `Idempotency-Key` | 201 (7.8초) |
| ⑧ | `GET /schedules/{scheduleId}` | 200 |
| ⑨ | `GET /schedules/{scheduleId}/map?dayNo=1` | 200 |

### 이번에 발견해 고친 것: Kakao size 상한

프론트는 `/places`를 `size=20`으로 부르는데 **Kakao Local 키워드 검색은 size 상한이 15**다.
서버가 이 값을 그대로 넘겨 Kakao가 400을 반환했고, 서버는 이를 `503 EXTERNAL_PROVIDER_UNAVAILABLE`로
바꿔 던졌다. 프론트는 이 코드를 잡아 `scope=INTERNAL`로 재시도하도록 되어 있어(`places.ts`)
화면에 오류는 보이지 않았지만, **Kakao 결과가 통째로 빠진 채 내부 DB 결과만 보이고 있었다.**

`PlaceService`가 Kakao 요청 size를 15로 클램프하도록 수정했다.

| 키워드 | 수정 전 | 수정 후 |
| --- | --- | --- |
| 해운대 해수욕장 | 503 → 내부 0건 | 14건 |
| 해운대 | 503 → 내부 8건 | 20건 (내부 9 + 외부 11) |
| 감천 문화마을 | 503 → 내부 0건 | 15건 |
| 돼지국밥 | 503 → 내부 1건 | 17건 |

## 4. 외부 Provider가 어디에 쓰이는지

| Provider | 사용처 | 호출 주체 |
| --- | --- | --- |
| Naver 지역검색 | 출발지 검색 | 프론트 (`/api/locations/search`) |
| Naver 이미지검색 | 장소 썸네일 | 프론트 (`/api/places/images`) |
| Kakao Local | `GET /places?scope=ALL`의 외부 결과 | 서버 |
| Kakao Local | `GET /locations/search` | 서버 — 프론트는 실패 시에만 이걸 쓰고 평소 Naver를 쓴다 |
| Kakao Local | `GET /places/{id}/nearby-facilities` | 서버 — **프론트 미연동** |
| ODsay | 대중교통 경로 | 서버 |
| TMAP | 보행 경로 좌표 | 서버 (`TMAP_WALKING_ENABLED`) |
| TourAPI | 장소 적재 | 서버 (스케줄러) |
| OpenAI / 테마 AI | Planner 보조 | 서버 — **로컬·dev 모두 비활성** |

가고싶은 장소 검색도 Naver로 옮기기로 했으므로, 그 작업이 끝나면 서버 쪽 Kakao 사용처는
`nearby-facilities` 하나만 남는다.

## 5. 알려진 갭

- **포트 불일치**: 프론트는 18080을 보는데 서버 기본 실행은 8080이다. 붙지 않으면 여기부터 확인한다.
- **step2가 질문 API를 쓰지 않는다**: `PACE`/`TRANSIT` 답변 ID가 화면에 하드코딩돼 있어 서버 질문 변경에
  자동으로 따라오지 않는다.
- **`nearby-facilities` 미연동**: 서버에 있으나 프론트가 부르지 않는다.
- **일정 수정(`PATCH /schedules/{id}`) 미연동**: 서버에 있으나 프론트가 부르지 않는다.
- **공유 링크(`/schedules/{id}/shares`) 미연동**: 서버에 있으나 프론트가 부르지 않는다.
- **AI Planner 비활성**: `AI_PLANNER_ENABLED`, `PLACE_THEME_AI_ENABLED`가 모두 꺼져 있어
  `planningMode`가 항상 `RULE_BASED`다.
- **입력값이 `sessionStorage`에만 있다**: 탭을 닫으면 작성 중이던 조건이 사라진다.

## 6. 막혔을 때 확인 순서

1. 서버가 프론트의 `BACKEND_API_URL` 포트로 떠 있는가.
2. `NEXT_PUBLIC_SCHEDULE_V2_MODE`가 `live`인가. `mock`이면 서버를 아예 부르지 않는다.
3. 응답의 `fieldErrors`를 본다. 필수 질문 누락, 좌표 범위 위반 등 사유가 `field`와 함께 나온다.
4. 그래도 원인이 안 잡히면 `traceId`로 서버 로그를 찾는다.
