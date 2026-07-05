# 부산 여행 일정 서비스 API 명세

## 공통

| 구분 | 내용 |
| --- | --- |
| Base URL | `/api/v1` |
| Content Type | `application/json; charset=utf-8` |
| 인증 | 1차 스프린트에서는 인증 없음 |
| 좌표 | WGS84, `longitude`는 경도, `latitude`는 위도 |
| 향후 계획 | 사용자 도메인 도입 후 JWT와 사용자별 일정 조회 적용 |

## 엔드포인트

| 기능 | Method | URI | 성공 상태 |
| --- | --- | --- | --- |
| 사전 질문 조회 | GET | `/trip-questions` | `200 OK` |
| 출발지·도착지 검색 | GET | `/locations/search` | `200 OK` |
| AI 일정 생성 | POST | `/schedules` | `201 Created` |
| 일정 목록 조회 | GET | `/schedules` | `200 OK` |
| 일정 수정 | PATCH | `/schedules/{scheduleId}` | `200 OK` |
| 장소 검색 | GET | `/places` | `200 OK` |
| 장소 상세 | GET | `/places/{placeId}` | `200 OK` |
| 주변 편의시설 | GET | `/places/{placeId}/nearby-facilities` | `200 OK` |
| 일정 지도 | GET | `/schedules/{scheduleId}/map` | `200 OK` |
| 공유 링크 생성 | POST | `/schedules/{scheduleId}/shares` | `201 Created` |
| 공유 일정 조회 | GET | `/shared-schedules/{token}` | `200 OK` |
| 공유 일정 지도 | GET | `/shared-schedules/{token}/map` | `200 OK` |
| 공유 링크 폐기 | DELETE | `/schedules/{scheduleId}/shares/{shareId}` | `204 No Content` |

## 1. 사전 질문 조회

`GET /api/v1/trip-questions`

요청 파라미터와 요청 본문이 없다.

```json
{
  "items": [
    {
      "id": "COMPANION",
      "text": "누구와 여행하나요?",
      "type": "SINGLE_CHOICE",
      "required": true,
      "displayOrder": 1,
      "answers": [
        {
          "id": "COMPANION_PARENTS",
          "label": "부모님과",
          "displayOrder": 1
        }
      ]
    }
  ]
}
```

## 2. 출발지·도착지 검색

`GET /api/v1/locations/search?keyword={keyword}&size={size}`

| Query | 필수 | 설명 |
| --- | :---: | --- |
| `keyword` | O | 검색할 장소명 |
| `size` | X | 최대 결과 수. 생략 시 `10` |

```json
{
  "items": [
    {
      "name": "부산역",
      "address": "부산 동구 중앙대로 206",
      "longitude": 129.0403,
      "latitude": 35.1151,
      "externalId": "kakao-place-id",
      "source": "KAKAO_LOCAL"
    }
  ]
}
```

Kakao Local 키워드 검색을 사용한다. 검색 결과 전체는 저장하지 않고 사용자가 선택한 이름과 좌표만 일정에 저장한다.

## 3. AI 다일 일정 생성

`POST /api/v1/schedules`

```json
{
  "startDate": "2026-06-23",
  "endDate": "2026-06-25",
  "dailyStartTime": "09:00",
  "dailyEndTime": "19:00",
  "startLocation": {
    "name": "부산역",
    "longitude": 129.0403,
    "latitude": 35.1151
  },
  "endLocation": {
    "name": "부산역",
    "longitude": 129.0403,
    "latitude": 35.1151
  },
  "selectedAnswers": [
    {
      "questionId": "COMPANION",
      "answerId": "COMPANION_PARENTS"
    },
    {
      "questionId": "THEME",
      "answerId": "THEME_LOCAL"
    },
    {
      "questionId": "PACE",
      "answerId": "PACE_BALANCED"
    }
  ],
  "mustVisitPlaceIds": [
    101,
    205
  ]
}
```

`mustVisitPlaceIds`는 사용자가 장소 검색에서 선택한 내부 `places.id` 목록이다. 생략할 수 있으며 전달된 장소는 일정 방문지와 대중교통 경로에 반드시 포함한다.

```json
{
  "id": "schedule-uuid",
  "status": "CONFIRMED",
  "startDate": "2026-06-23",
  "endDate": "2026-06-25",
  "styleSummary": "부모님과 함께하는 로컬 중심 일정",
  "days": [
    {
      "dayNo": 1,
      "date": "2026-06-23",
      "stops": [
        {
          "id": "stop-uuid",
          "order": 1,
          "stayMinutes": 60,
          "place": {
            "id": 101,
            "name": "이송도전망대",
            "longitude": 129.047956,
            "latitude": 35.075519
          },
          "inboundTransit": {
            "totalMinutes": 25,
            "fareAmount": 1550,
            "segments": [
              {
                "mode": "BUS",
                "lineName": "26",
                "startStationName": "부산역",
                "endStationName": "남부민2동"
              }
            ]
          }
        }
      ],
      "finalTransit": null
    }
  ]
}
```

일정 생성에는 TourAPI 장소 데이터, AI 일정 배치, ODsay 대중교통 경로를 사용한다. 대중교통 승하차 전후 도보 구간의 지도 경로선은 TMAP 보행자 경로를 우선 사용하고, 실패하면 출발·도착 좌표를 잇는 fallback 선을 사용한다. 부산 버스·도시철도 API는 보조 검증 또는 실시간 정보 제공에 사용할 수 있다.

## 4. 일정 목록 조회

`GET /api/v1/schedules`

요청 파라미터와 요청 본문이 없다. 1차 스프린트에서는 저장된 전체 일정을 반환한다.

```json
{
  "items": [
    {
      "id": "schedule-uuid",
      "status": "CONFIRMED",
      "startDate": "2026-06-23",
      "endDate": "2026-06-25",
      "styleSummary": "부모님과 함께하는 로컬 중심 일정",
      "days": [
        {
          "dayNo": 1,
          "date": "2026-06-23",
          "stops": [
            {
              "id": "stop-uuid",
              "place": {
                "id": 101,
                "name": "이송도전망대"
              },
              "inboundTransit": {
                "totalMinutes": 25
              }
            }
          ]
        }
      ]
    }
  ]
}
```

향후 JWT 도입 시 URI는 유지하고 인증 사용자 ID에 해당하는 일정만 반환한다.

## 5. 일정 수정 및 경로 재계산

`PATCH /api/v1/schedules/{scheduleId}`

```json
{
  "stops": [
    {
      "stopId": "stop-a",
      "dayNo": 1,
      "order": 1,
      "stayMinutes": 70
    },
    {
      "placeId": 205,
      "dayNo": 1,
      "order": 2,
      "stayMinutes": 90
    }
  ]
}
```

- 기존 방문 계획은 `stopId`를 전달한다.
- 새 장소는 `placeId`를 전달한다.
- 한 항목에 `stopId`와 `placeId`를 동시에 전달하지 않는다.
- `stops`는 수정 후 일정에 남길 전체 방문 계획이다.
- `dayNo`는 방문 계획을 배치할 여행 일차다.
- 편집 토큰과 `version`을 사용하지 않는다.

```json
{
  "id": "schedule-uuid",
  "status": "CONFIRMED",
  "days": [
    {
      "dayNo": 1,
      "stops": [
        {
          "id": "stop-a",
          "order": 1,
          "stayMinutes": 70
        },
        {
          "id": "stop-new",
          "order": 2,
          "stayMinutes": 90
        }
      ]
    }
  ]
}
```

추가·삭제·순서·일차·체류시간 변경을 반영하고 영향을 받는 대중교통 경로를 다시 계산한다.

## 6. 장소 검색·교체 후보 조회

`GET /api/v1/places`

키워드 검색:

```http
GET /api/v1/places?keyword=전망대
```

위치 기반 검색:

```http
GET /api/v1/places?longitude=129.0403&latitude=35.1151&radius=1000
```

키워드 검색은 `keyword`를 사용한다. 위치 기반 검색은 `longitude`, `latitude`, `radius`를 사용한다. 두 방식 모두 내부 DB의 `places`를 조회한다.

```json
{
  "items": [
    {
      "id": 101,
      "externalContentId": "126508",
      "name": "이송도전망대",
      "category": "관광지",
      "address": "부산 서구 암남동",
      "longitude": 129.047956,
      "latitude": 35.075519,
      "distanceMeters": 850,
      "primaryImageUrl": "https://example.com/image.jpg"
    }
  ]
}
```

`distanceMeters`는 위치 기반 검색에서만 계산하며 DB에 저장하지 않는다.

## 7. 장소 상세 조회

`GET /api/v1/places/{placeId}`

```json
{
  "id": 101,
  "externalContentId": "126508",
  "contentTypeId": "12",
  "name": "이송도전망대",
  "address": "부산 서구 암남동",
  "longitude": 129.047956,
  "latitude": 35.075519,
  "overview": "장소 설명",
  "operatingInfo": {
    "openingHoursText": "09:00~18:00",
    "closedDaysText": "연중무휴",
    "useFeeText": "무료",
    "parkingText": "주차 가능",
    "requiresManualCheck": true
  },
  "images": [
    {
      "url": "https://example.com/image.jpg",
      "thumbnailUrl": "https://example.com/thumbnail.jpg",
      "copyrightType": "Type1"
    }
  ]
}
```

TourAPI 기본·상세·소개·이미지 응답을 내부 DB에 적재한 결과를 조회한다.

## 8. 주변 편의시설 조회

`GET /api/v1/places/{placeId}/nearby-facilities?types=CONVENIENCE_STORE&radius=1000`

`placeId`의 좌표를 기준으로 주변 편의시설을 검색한다. 1차 스프린트에서 지원하는 유형은 `CONVENIENCE_STORE`이며 Kakao Local 카테고리 `CS2`를 사용한다.

```json
{
  "items": [
    {
      "externalId": "kakao-place-id",
      "type": "CONVENIENCE_STORE",
      "name": "CU 부산역점",
      "address": "부산 동구 중앙대로",
      "longitude": 129.041,
      "latitude": 35.115,
      "distanceMeters": 120,
      "placeUrl": "https://place.map.kakao.com/...",
      "source": "KAKAO_LOCAL"
    }
  ]
}
```

검색 결과는 실시간 데이터이므로 DB에 저장하지 않는다. `ATM`, `RESTROOM` 등 지원하지 않는 유형은 `501 FACILITY_TYPE_NOT_SUPPORTED`를 반환한다.

## 9. 일정 지도 데이터 조회

`GET /api/v1/schedules/{scheduleId}/map?dayNo={dayNo}`

`dayNo`는 선택값이며 생략하면 전체 일차 데이터를 반환한다.

```json
{
  "startMarker": {
    "name": "부산역",
    "longitude": 129.0403,
    "latitude": 35.1151
  },
  "endMarker": {
    "name": "부산역",
    "longitude": 129.0403,
    "latitude": 35.1151
  },
  "markers": [
    {
      "dayNo": 1,
      "order": 1,
      "placeId": 101,
      "name": "이송도전망대",
      "longitude": 129.047956,
      "latitude": 35.075519
    }
  ],
  "routeLines": [
    {
      "dayNo": 1,
      "routeOrder": 1,
      "lineOrder": 1,
      "mode": "WALK",
      "lineName": null,
      "startName": "부산역",
      "endName": "부산역",
      "coordinates": [
        [129.0403, 35.1151],
        [129.0412, 35.1146]
      ]
    },
    {
      "dayNo": 1,
      "routeOrder": 1,
      "lineOrder": 2,
      "mode": "BUS",
      "lineName": "26",
      "startName": "부산역",
      "endName": "남부민2동",
      "coordinates": [
        [129.0412, 35.1146],
        [129.0470, 35.0780]
      ]
    },
    {
      "dayNo": 1,
      "routeOrder": 1,
      "lineOrder": 3,
      "mode": "WALK",
      "lineName": null,
      "startName": "남부민2동",
      "endName": "이송도전망대",
      "coordinates": [
        [129.0470, 35.0780],
        [129.047956, 35.075519]
      ]
    }
  ]
}
```

서버는 저장된 경로 좌표를 반환하고 프론트엔드는 Kakao Maps SDK로 마커와 선을 그린다. `routeLines[].startName`과 `routeLines[].endName`은 해당 선 조각의 출발·도착 지점명이다. 대중교통 구간은 승하차 정류장 또는 역 이름을, 도보 구간은 출발지·목적지·승하차 지점을 기준으로 채운다. 대중교통 승하차 전후 도보 구간은 `mode`가 `WALK`인 경로선으로 함께 반환한다. `WALK` 경로선은 TMAP 보행자 경로 좌표를 우선 사용하며, 외부 API 장애 또는 좌표 누락 시 출발·도착 좌표를 잇는 fallback 선을 반환한다. 지도 기본 표시는 `BUS`, `SUBWAY` 같은 대중교통 경로선을 우선하고, `WALK` 경로선은 사용자가 도보 구간 확인을 요청할 때 선택 오버레이로 표시할 수 있다. 왕복 일정처럼 여러 이동 경로가 같은 지도에 겹칠 수 있으므로 클라이언트는 `routeOrder` 기준으로 선택 표시할 수 있다.

## 10. 공유 링크 생성

`POST /api/v1/schedules/{scheduleId}/shares`

```json
{
  "expiresInDays": 30
}
```

```json
{
  "id": "share-uuid",
  "token": "share-token",
  "url": "/shared-schedules/share-token",
  "expiresAt": "2026-07-23T12:00:00+09:00"
}
```

`expiresInDays`는 생략할 수 있다. 응답에서만 원본 토큰을 반환하고 DB에는 해시를 저장한다.

## 11. 공유 일정 조회

`GET /api/v1/shared-schedules/{token}`

```json
{
  "id": "schedule-uuid",
  "status": "CONFIRMED",
  "readOnly": true,
  "days": [
    {
      "dayNo": 1,
      "stops": [
        {
          "place": {
            "name": "이송도전망대"
          }
        }
      ]
    }
  ]
}
```

## 12. 공유 일정 지도 조회

`GET /api/v1/shared-schedules/{token}/map?dayNo={dayNo}`

```json
{
  "startMarker": {
    "name": "부산역",
    "longitude": 129.0403,
    "latitude": 35.1151
  },
  "endMarker": {
    "name": "부산역",
    "longitude": 129.0403,
    "latitude": 35.1151
  },
  "markers": [],
  "routeLines": []
}
```

응답 구조는 일정 지도 데이터 조회와 동일하다.

## 13. 공유 링크 폐기

`DELETE /api/v1/schedules/{scheduleId}/shares/{shareId}`

요청 본문은 없다.

```http
204 No Content
```

`share_links.revoked_at`을 갱신한다.

## PDF·이미지 저장

별도 백엔드 API를 만들지 않는다. 프론트엔드에서 브라우저 인쇄, `jsPDF`, `html2canvas` 등을 사용해 생성한다.

## 공통 오류 응답

```json
{
  "code": "INVALID_SCHEDULE_CONDITION",
  "message": "일정 조건이 올바르지 않습니다.",
  "fieldErrors": [],
  "traceId": "01J..."
}
```

| HTTP | 오류 코드 | 상황 |
| --- | --- | --- |
| 400 | `INVALID_SCHEDULE_CONDITION` | 일정 조건 또는 요청값이 잘못됨 |
| 404 | `SCHEDULE_NOT_FOUND` | 일정을 찾을 수 없음 |
| 404 | `PLACE_NOT_FOUND` | 장소를 찾을 수 없음 |
| 404 | `SHARE_LINK_NOT_FOUND` | 공유 링크가 없거나 폐기됨 |
| 422 | `TRANSIT_ROUTE_NOT_FOUND` | 장소 사이 대중교통 경로를 찾지 못함 |
| 501 | `FACILITY_TYPE_NOT_SUPPORTED` | 지원하지 않는 편의시설 유형 |
| 503 | `EXTERNAL_PROVIDER_UNAVAILABLE` | 외부 서비스가 응답하지 않음 |

## 1차 스프린트 제외

- 인증과 사용자 도메인
- 편집 토큰과 일정 `version`
- 예산 계산
- 날씨 대응
- ATM과 공중화장실 검색
- 오디오 가이드와 두루누비
