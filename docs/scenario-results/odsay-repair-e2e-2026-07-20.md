# ODsay Repair E2E - 2026-07-20

`refactor/schedule-repair-engine`의 실제 Provider E2E 결과다. 동일 요청을 ODsay 활성 서버와 Fake Provider 서버에 각각 한 번 전송했다. API Key와 응답 원문은 저장하지 않는다.

## Fixture

| 항목 | 값 |
| --- | --- |
| 날짜 | `2026-08-10`, 09:00~19:00 |
| 출발·도착 | 부산역 → 부산역 |
| 필수 장소 | `places.id=95` (`산복도로`) |
| 선호 | 친구, 일반 이동, 여유, 환승 적게, 문화·역사 |
| 서버 | PR 4 코드, 로컬 PostgreSQL, dummy place seed |

## 결과

| 항목 | 실제 ODsay | Fake Provider |
| --- | ---: | ---: |
| HTTP·Hard Gate | `201`·PASS | `201`·PASS |
| 방문 장소 수 | 4 | 4 |
| 총 이동시간 | 67분 | 95분 |
| 총 환승 | 0 | 0 |
| 품질 점수 | 80/100 | 81/100 |
| 경로 신뢰도 | `MEDIUM` | `LOW` |
| 생성시간 | 3,721ms | 367ms |
| 외부 HTTP | 7 | 0 |
| ODsay 검색·상세 선형 | 5·2 | 0·0 |

실제 응답은 부산 1호선 `남포 → 토성`과 `토성 → 부산역` 구간을 포함했다. 지도 응답의 지하철 선형 좌표 수는 각각 74개와 136개였고, Fake Provider는 `FAKE-BUS` 직선 좌표 2개만 반환했다. 짧은 도보 연결선은 실제 ODsay 응답에서도 2점 fallback이므로 `routeConfidence=MEDIUM`이다.

이 Fixture는 실제 경로와 지도 선형·운영 지표를 검증하는 목적이다. 시간 초과 Repair 전략은 Provider 없는 단위 테스트와 상세 경로가 긴 서비스 회귀 테스트로 별도 검증했다. 단일 cold 실행이므로 성능 p95 수치로 해석하지 않는다.
