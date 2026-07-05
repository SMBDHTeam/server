# API 변경 이력

API 계약이 변경될 때마다 최신 항목을 위에 추가한다.

## 작성 형식

```md
## YYYY-MM-DD

- API: `METHOD /api/v1/...`
- 구분: 추가 | 변경 | 삭제 | 수정
- 이전:
- 이후:
- 이유:
- 호환성 파괴: 예 | 아니오
- 관련 PR 또는 이슈:
```

## 2026-07-02

- API: `GET /api/v1/schedules/{scheduleId}/map`, `GET /api/v1/shared-schedules/{token}/map`
- 구분: 추가
- 이전: `routeLines`에서 이동수단, 노선명, 좌표만 확인 가능
- 이후: `routeLines[].startName`, `routeLines[].endName`으로 각 선 조각의 출발·도착 지점명 반환
- 이유: 지도에서 도보, 정류장, 역 구간을 사용자가 구분할 수 있게 하기 위함
- 호환성 파괴: 아니오
- 관련 PR 또는 이슈: 없음

- API: `GET /api/v1/schedules/{scheduleId}/map`, `GET /api/v1/shared-schedules/{token}/map`
- 구분: 변경
- 이전: `mode: "WALK"` 경로선은 출발·도착 좌표를 잇는 fallback 선만 반환
- 이후: TMAP 보행자 경로 좌표를 우선 반환하고, 실패 시 기존 fallback 선을 반환
- 이유: 지도에서 도보 이동 구간을 실제 보행 경로에 가깝게 표시하기 위함
- 호환성 파괴: 아니오
- 관련 PR 또는 이슈: 없음

- API: `GET /api/v1/schedules/{scheduleId}/map`, `GET /api/v1/shared-schedules/{token}/map`
- 구분: 변경
- 이전: `routeLines`에는 대중교통 노선 좌표만 포함될 수 있었음
- 이후: 대중교통 승하차 전후 도보 구간도 기존 `routeLines` 배열에 `mode: "WALK"` 항목으로 포함
- 이유: 지도에서 출발지-승차 지점, 하차 지점-목적지 이동이 끊겨 보이지 않게 하기 위함
- 호환성 파괴: 아니오
- 관련 PR 또는 이슈: 없음

## 2026-06-25

- API: 1차 스프린트 전체 API
- 구분: 추가
- 이전: 저장소 내 기준 API 명세 없음
- 이후: `docs/API_SPEC.md`와 `docs/API_FIELD_GUIDE.md`를 기준 계약으로 지정
- 이유: 백엔드 구현과 AI Agent 작업 기준 통일
- 호환성 파괴: 아니오
- 관련 PR 또는 이슈: 없음
