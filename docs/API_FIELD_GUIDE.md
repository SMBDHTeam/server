# API 데이터 필드 설명

`docs/API_SPEC.md`의 JSON 필드 의미를 설명한다.

| 표기 | 의미 |
| --- | --- |
| O | 반드시 전달하거나 반환하는 값 |
| X | 생략하거나 `null`일 수 있는 값 |
| 조건부 | 특정 요청 방식에서만 필요한 값 |
| object | 여러 필드를 묶은 JSON 객체 |
| array | 같은 종류의 값을 여러 개 담는 JSON 목록 |
| `items[]` | `items` 배열 안의 항목 한 개 |

## 1. 사전 질문 조회

`GET /api/v1/trip-questions`

요청값은 없다.

| 응답 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `items` | array | O | 질문 목록 |
| `items[].id` | string | O | 질문 고정 ID |
| `items[].text` | string | O | 화면에 표시할 질문 |
| `items[].type` | string | O | 선택 방식. 1차는 `SINGLE_CHOICE` |
| `items[].required` | boolean | O | 필수 질문 여부 |
| `items[].displayOrder` | integer | O | 질문 표시 순서 |
| `items[].answers` | array | O | 선택 가능한 답변 목록 |
| `items[].answers[].id` | string | O | 답변 고정 ID |
| `items[].answers[].label` | string | O | 답변 버튼 문구 |
| `items[].answers[].displayOrder` | integer | O | 답변 표시 순서 |

## 2. 출발지·도착지 검색

`GET /api/v1/locations/search`

| 요청 필드 | 위치 | 자료형 | 필수 | 의미 |
| --- | --- | --- | :---: | --- |
| `keyword` | Query | string | O | 검색할 장소명 |
| `size` | Query | integer | X | 최대 결과 수. 생략 시 `10` |

| 응답 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `items` | array | O | Kakao Local 검색 결과 |
| `items[].name` | string | O | 장소명 |
| `items[].address` | string | X | 주소 |
| `items[].longitude` | number | O | 경도 |
| `items[].latitude` | number | O | 위도 |
| `items[].externalId` | string | O | 카카오 장소 ID |
| `items[].source` | string | O | `KAKAO_LOCAL` |

## 3. AI 다일 일정 생성

`POST /api/v1/schedules`

| 요청 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `startDate` | string(date) | O | 여행 시작일 |
| `endDate` | string(date) | O | 여행 종료일 |
| `dailyStartTime` | string(time) | O | 매일 일정 시작시각 |
| `dailyEndTime` | string(time) | O | 매일 일정 종료시각 |
| `startLocation` | object | O | 첫날 출발 위치 |
| `startLocation.name` | string | O | 출발지명 |
| `startLocation.longitude` | number | O | 출발지 경도 |
| `startLocation.latitude` | number | O | 출발지 위도 |
| `endLocation` | object | O | 마지막 도착 위치 |
| `endLocation.name` | string | O | 도착지명 |
| `endLocation.longitude` | number | O | 도착지 경도 |
| `endLocation.latitude` | number | O | 도착지 위도 |
| `selectedAnswers` | array | O | 선택한 질문과 답변 목록 |
| `selectedAnswers[].questionId` | string | O | `questions.id` |
| `selectedAnswers[].answerId` | string | O | `answers.id` |
| `mustVisitPlaceIds` | array | X | 필수 방문 장소 ID 목록 |
| `mustVisitPlaceIds[]` | integer | O | 내부 `places.id` 한 개 |

| 응답 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `id` | string(UUID) | O | 일정 ID |
| `status` | string | O | 일정 상태 |
| `startDate` | string(date) | O | 여행 시작일 |
| `endDate` | string(date) | O | 여행 종료일 |
| `styleSummary` | string | X | 답변 기반 여행 스타일 요약 |
| `days` | array | O | 날짜별 일정 |
| `days[].dayNo` | integer | O | 여행 일차 |
| `days[].date` | string(date) | O | 실제 날짜 |
| `days[].stops` | array | O | 해당 날짜의 방문 계획 |
| `days[].stops[].id` | string(UUID) | O | 방문 계획 ID |
| `days[].stops[].order` | integer | O | 날짜 안에서 방문 순서 |
| `days[].stops[].stayMinutes` | integer | O | 체류시간(분) |
| `days[].stops[].place` | object | O | 방문 장소 |
| `days[].stops[].place.id` | integer | O | 내부 장소 ID |
| `days[].stops[].place.name` | string | O | 장소명 |
| `days[].stops[].place.longitude` | number | O | 장소 경도 |
| `days[].stops[].place.latitude` | number | O | 장소 위도 |
| `days[].stops[].inboundTransit` | object | X | 이전 지점에서 현재 장소로 들어오는 경로 |
| `days[].stops[].inboundTransit.totalMinutes` | integer | O | 전체 이동시간(분) |
| `days[].stops[].inboundTransit.fareAmount` | integer | X | 예상 요금(원) |
| `days[].stops[].inboundTransit.segments` | array | O | 교통 구간 목록 |
| `segments[].mode` | string | O | `WALK`, `BUS`, `SUBWAY` |
| `segments[].lineName` | string | X | 버스 번호 또는 호선 |
| `segments[].startStationName` | string | X | 승차 정류장 또는 출발역 |
| `segments[].endStationName` | string | X | 하차 정류장 또는 도착역 |
| `days[].finalTransit` | object/null | X | 마지막 장소에서 최종 도착지까지 경로 |
| `days[].finalTransit.totalMinutes` | integer | O | 최종 도착지까지 이동시간(분) |
| `days[].finalTransit.fareAmount` | integer | X | 최종 도착지까지 예상 요금(원) |
| `days[].finalTransit.segments` | array | O | 최종 도착지까지 교통 구간 목록 |

## 4. 일정 목록 조회

`GET /api/v1/schedules`

요청값은 없으며 전체 일정 목록을 반환한다. 응답의 일정 객체는 일정 생성 응답과 같은 의미를 가진다.

| 응답 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `items` | array | O | 저장된 일정 목록 |
| `items[].id` | string(UUID) | O | 일정 ID |
| `items[].status` | string | O | 일정 상태 |
| `items[].startDate` | string(date) | O | 시작일 |
| `items[].endDate` | string(date) | O | 종료일 |
| `items[].styleSummary` | string | X | 여행 스타일 요약 |
| `items[].days` | array | O | 날짜별 일정 |

## 5. 일정 수정

`PATCH /api/v1/schedules/{scheduleId}`

| 요청 필드 | 위치 | 자료형 | 필수 | 의미 |
| --- | --- | --- | :---: | --- |
| `scheduleId` | Path | string(UUID) | O | 수정할 일정 |
| `stops` | Body | array | O | 수정 후 전체 방문 계획 |
| `stops[].stopId` | Body | string(UUID) | 조건부 | 유지 또는 수정할 기존 방문 계획 |
| `stops[].placeId` | Body | integer | 조건부 | 새로 추가할 내부 장소 |
| `stops[].dayNo` | Body | integer | O | 배치할 여행 일차 |
| `stops[].order` | Body | integer | O | 해당 일차의 방문 순서 |
| `stops[].stayMinutes` | Body | integer | O | 체류시간(분) |

`stopId`와 `placeId`는 하나만 전달한다. `version`은 사용하지 않는다.

| 응답 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `id` | string(UUID) | O | 수정된 일정 ID |
| `status` | string | O | 일정 상태 |
| `days` | array | O | 수정된 날짜별 일정 |
| `days[].dayNo` | integer | O | 여행 일차 |
| `days[].stops` | array | O | 수정된 방문 계획 |
| `days[].stops[].id` | string(UUID) | O | 방문 계획 ID |
| `days[].stops[].order` | integer | O | 방문 순서 |
| `days[].stops[].stayMinutes` | integer | O | 체류시간 |

## 6. 장소 검색

`GET /api/v1/places`

| 요청 필드 | 위치 | 자료형 | 필수 | 의미 |
| --- | --- | --- | :---: | --- |
| `keyword` | Query | string | 조건부 | 이름 기반 내부 DB 검색어 |
| `longitude` | Query | number | 조건부 | 위치 검색 중심 경도 |
| `latitude` | Query | number | 조건부 | 위치 검색 중심 위도 |
| `radius` | Query | integer | X | 검색 반경(m) |

| 응답 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `items` | array | O | 장소 결과 |
| `items[].id` | integer | O | 내부 장소 ID |
| `items[].externalContentId` | string | O | TourAPI 콘텐츠 ID |
| `items[].name` | string | O | 장소명 |
| `items[].category` | string | X | 표시용 분류 |
| `items[].address` | string | X | 주소 |
| `items[].longitude` | number | O | 경도 |
| `items[].latitude` | number | O | 위도 |
| `items[].distanceMeters` | integer/null | X | 검색 중심과 거리 |
| `items[].primaryImageUrl` | string/null | X | 대표 이미지 |

## 7. 장소 상세

`GET /api/v1/places/{placeId}`

| 응답 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `id` | integer | O | 내부 장소 ID |
| `externalContentId` | string | O | TourAPI 콘텐츠 ID |
| `contentTypeId` | string | X | TourAPI 관광 타입 |
| `name` | string | O | 장소명 |
| `address` | string | X | 주소 |
| `longitude` | number | O | 경도 |
| `latitude` | number | O | 위도 |
| `overview` | string | X | 상세 설명 |
| `operatingInfo` | object | X | 운영 정보 |
| `operatingInfo.openingHoursText` | string | X | 운영시간 원문 |
| `operatingInfo.closedDaysText` | string | X | 휴무일 원문 |
| `operatingInfo.useFeeText` | string | X | 이용요금 원문 |
| `operatingInfo.parkingText` | string | X | 주차 원문 |
| `operatingInfo.requiresManualCheck` | boolean | O | 원문 직접 확인 필요 여부 |
| `images` | array | O | 이미지 목록 |
| `images[].url` | string | O | 원본 이미지 |
| `images[].thumbnailUrl` | string | X | 썸네일 |
| `images[].copyrightType` | string | X | 저작권 구분 |

## 8. 주변 편의시설

`GET /api/v1/places/{placeId}/nearby-facilities`

| 요청 필드 | 위치 | 자료형 | 필수 | 의미 |
| --- | --- | --- | :---: | --- |
| `placeId` | Path | integer | O | 검색 기준 장소 |
| `types` | Query | string | O | 1차 지원값 `CONVENIENCE_STORE` |
| `radius` | Query | integer | X | 검색 반경(m) |

| 응답 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `items` | array | O | 주변 편의점 목록 |
| `items[].externalId` | string | O | 카카오 장소 ID |
| `items[].type` | string | O | `CONVENIENCE_STORE` |
| `items[].name` | string | O | 시설명 |
| `items[].address` | string | X | 주소 |
| `items[].longitude` | number | O | 경도 |
| `items[].latitude` | number | O | 위도 |
| `items[].distanceMeters` | integer | X | 기준 장소와 거리 |
| `items[].placeUrl` | string | X | 카카오 장소 URL |
| `items[].source` | string | O | `KAKAO_LOCAL` |

## 9. 일정 지도

`GET /api/v1/schedules/{scheduleId}/map`

| 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `scheduleId` | string(UUID) | O | 일정 ID |
| `dayNo` | integer | X | 조회할 일차 |
| `startMarker` | object | O | 출발지 마커 |
| `endMarker` | object | O | 도착지 마커 |
| `markers` | array | O | 방문 장소 마커 |
| `markers[].dayNo` | integer | O | 여행 일차 |
| `markers[].order` | integer | O | 방문 순서 |
| `markers[].placeId` | integer | O | 장소 ID |
| `markers[].name` | string | O | 장소명 |
| `markers[].longitude` | number | O | 경도 |
| `markers[].latitude` | number | O | 위도 |
| `routeLines` | array | O | 대중교통 경로선 |
| `routeLines[].dayNo` | integer | O | 경로 일차 |
| `routeLines[].routeOrder` | integer | O | 날짜 내 경로 순서 |
| `routeLines[].lineOrder` | integer | O | 선 조각 순서 |
| `routeLines[].mode` | string | O | 이동수단 |
| `routeLines[].lineName` | string | X | 노선명 |
| `routeLines[].coordinates` | array | O | `[경도, 위도]` 좌표 목록 |

## 10. 공유 링크 생성

| 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `scheduleId` | string(UUID) | O | 공유할 일정 |
| `expiresInDays` | integer | X | 유효기간(일) |
| `id` | string(UUID) | O | 공유 링크 관리 ID |
| `token` | string | O | 공유 URL 토큰 |
| `url` | string | O | 공유 상대 URL |
| `expiresAt` | string(datetime) | X | 만료시각 |

## 11. 공유 일정·지도 조회

| 필드 | 자료형 | 필수 | 의미 |
| --- | --- | :---: | --- |
| `token` | string | O | 공유 링크 토큰 |
| `id` | string(UUID) | O | 공유 대상 일정 |
| `status` | string | O | 일정 상태 |
| `readOnly` | boolean | O | 항상 `true` |
| `days` | array | O | 날짜별 일정 |

공유 지도 응답 필드는 일반 일정 지도 응답과 동일하다.

## 12. 공유 링크 폐기

`DELETE /api/v1/schedules/{scheduleId}/shares/{shareId}`

| 요청 필드 | 위치 | 자료형 | 필수 | 의미 |
| --- | --- | --- | :---: | --- |
| `scheduleId` | Path | string(UUID) | O | 일정 ID |
| `shareId` | Path | string(UUID) | O | 폐기할 공유 링크 ID |

응답 본문 없이 `204 No Content`를 반환한다.
