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

## 2026-07-12

- API: `POST /api/v1/schedules`
- 구분: 변경
- 이전: 생성 응답에서 일정과 경로만 반환하고 Hard Gate·품질 점수·외부 Provider 실행 비용은 테스트에서만 확인
- 이후: `evaluation`으로 Hard Gate, 100점 품질 지표, 생성시간·경로 캐시·Provider 호출, ODsay 경로검색·loadLane·TMAP 실제 HTTP 호출, 경로·geometry fallback 운영 지표를 반환
- 이유: 실제 다일 일정 생성 품질과 성능을 같은 기준으로 반복 측정하기 위함
- 호환성 파괴: 아니오. 기존 필드는 유지하고 평가 필드를 추가함
- 관련 PR 또는 이슈: 없음

- API: `POST /api/v1/schedules`
- 구분: 변경
- 이전: 전역 `startLocation`, `endLocation`, `dailyStartTime`, `dailyEndTime`을 모든 일차에 동일하게 적용
- 이후: 선택 필드 `days`로 각 일차의 출발지, 도착지, 시작시각, 종료시각을 전달하고 응답의 각 일차에도 `startLocation`, `endLocation`을 반환. 필수 방문 장소 외 남은 슬롯은 자동 추천으로 채우며 실제 이동시간 기준으로 체류시간을 조정
- 이유: 2박 3일처럼 숙소 이동과 첫날·마지막 날 가용시간 차이가 있는 다일 일정을 지원하기 위함
- 호환성 파괴: 아니오. `days` 생략 시 기존 전역 조건 적용
- 관련 PR 또는 이슈: 없음

- API: `POST /api/v1/schedules`
- 구분: 수정
- 이전: `selectedAnswers`가 비어 있지 않은지만 검증하고 질문·답변 관계와 필수 질문 누락은 검사하지 않음
- 이후: 활성 필수 질문별 단일 답변, 질문·답변 소속 관계, 중복 질문을 검증
- 이유: 의미 없는 문자열 답변이 일정 추천 휴리스틱에 전달되는 것을 차단하기 위함
- 호환성 파괴: 예. 필수 질문이 누락되거나 잘못 연결된 기존 요청은 `400 INVALID_SCHEDULE_CONDITION` 반환
- 관련 PR 또는 이슈: 없음

## 2026-07-08

- API: `POST /api/v1/schedules`
- 구분: 변경
- 이전: 일정 생성 응답이 방문 순서, 체류시간, 총 이동시간, 요금, 노선명, 승하차명 중심으로 반환됨
- 이후: 일자별 시작/종료시각, 하루 요약, 방문지 도착/출발 예정시각, 장소 카테고리·주소·대표이미지·운영정보, 경로 요약, 경로 출발/도착 예정시각, 도보시간, 대기시간, 환승 횟수, provider, 실시간 반영 상태, fallback 여부, 구간 안내문, 구간 소요시간·거리·정류장 수, 추천 이유, 주의사항을 반환
- 이유: 생성된 일정을 내부 평가가 아니라 사용자가 그대로 따라갈 수 있는 상세 일정으로 전달하기 위함
- 호환성 파괴: 아니오. 기존 필드는 유지하고 상세 필드를 추가함
- 관련 PR 또는 이슈: 없음

- API: `GET /api/v1/schedules/{scheduleId}/map`
- 구분: 변경
- 이전: 지도 응답의 마커와 경로선이 좌표, 순서, 노선명, 출발·도착 지점명 중심으로 반환됨
- 이후: 마커에 도착/출발 예정시각, 보조 문구, 위험 수준을 추가하고, 경로선에 소요시간, 거리, 안내문, fallback 여부를 추가
- 이유: 지도 화면에서 일정 타임라인과 경로 설명을 함께 보여주기 위함
- 호환성 파괴: 아니오. 기존 필드는 유지하고 상세 필드를 추가함
- 관련 PR 또는 이슈: 없음

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
