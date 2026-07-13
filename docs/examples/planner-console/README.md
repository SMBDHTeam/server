# Planner E2E Console

일정 생성 API를 브라우저에서 호출하고 일정 응답과 일차별 지도 응답을 함께 확인하는 로컬 개발 도구다.

## 실행

`.env`의 ODsay와 TMAP 키를 로드한 뒤 백엔드를 `18080` 포트로 실행한다.

```bash
set -a
source .env
set +a
./gradlew bootRun --args='--server.port=18080 --app.seed.dummy-place.enabled=true --external.odsay.enabled=true --external.tmap.walking.enabled=true'
```

프로젝트 루트에서 문서 서버를 실행한다.

```bash
python3 -m http.server 8080 --bind 127.0.0.1 --directory docs/examples
```

브라우저에서 `http://localhost:8080/planner-console/`을 연다.

`app.seed.dummy-place.enabled=true`는 비어 있는 로컬 DB에 일정 생성용 장소 fixture를 넣는다. 이미 장소 데이터가 있는 DB를 사용할 때는 생략할 수 있다.

## 개발 서버 확인

개발 서버 API Base URL은 `http://13.209.73.113/api/v1`이다. 콘솔 상단 API 입력값을 이 주소로 바꾸면 같은 요청을 개발 서버에 보낼 수 있다.

먼저 질문 seed 적용 여부를 확인한다.

```bash
curl -fsS http://13.209.73.113/api/v1/trip-questions | jq '.items | length'
```

결과가 `0`이면 일정 생성에 필요한 질문·답변 seed가 적용되지 않은 상태다. 배포 반영 후 질문이 5개인지 확인하고 E2E 일정을 생성한다.

실제 대중교통 경로는 ODsay 경로 탐색과 `loadLane` 상세 좌표를 사용한다. 승하차 전후 도보 구간은 TMAP 보행자 경로를 사용하며, 상세 좌표를 얻지 못한 구간은 지도 범례에 `fallback`으로 표시한다. 외부 Provider를 끄면 `FAKE` 직선 경로가 표시되므로 실제 루트 검증 결과로 사용하지 않는다.

## 지도 설정

Kakao 지도는 저장소에 포함하지 않는 `docs/examples/kakao-map-local-config.js`의 JavaScript 키를 사용한다.

```javascript
window.KAKAO_JAVASCRIPT_KEY = "local-javascript-key";
```

키가 없거나 SDK 호출이 실패하면 일정 지도 API 좌표를 이용한 SVG 미리보기로 전환한다. 키를 URL 파라미터에 넣지 않는다.

## 검증 흐름

1. `GET /api/v1/trip-questions`로 활성 질문을 조회한다.
2. 입력한 다일 조건을 `POST /api/v1/schedules`에 전달한다.
3. 생성된 ID와 선택한 일차로 `GET /api/v1/schedules/{scheduleId}/map?dayNo={dayNo}`를 호출한다.
4. Hard Gate, 100점 품질표, ODsay·loadLane·TMAP 호출, 캐시·경로/geometry fallback 운영 지표를 확인한다.
5. 일정, 지도, 실제 요청·응답 JSON을 교차 확인한다.
