package com.server.place.controller;

/**
 * 외부 장소 확정 요청 예시.
 *
 * <p>제공자마다 좌표 형식과 ID 규칙이 달라, 예시가 없으면 클라이언트가 명세를 일일이
 * 확인해야 한다. 특히 네이버는 안정적인 장소 ID를 주지 않아 {@code mapx-mapy} 합성 키를 쓴다.
 */
final class PlaceOpenApiExamples {

    /** 네이버 지역검색 응답을 그대로 옮긴 형태. mapx/mapy는 10000000으로 나눠 보낸다. */
    static final String RESOLVE_NAVER = """
            {
              "source": "NAVER_LOCAL",
              "externalId": "1291598546-351585232",
              "name": "해운대 해수욕장",
              "category": "여행,명소>관광,명소>해수욕장",
              "address": "부산 해운대구 우동",
              "longitude": 129.1598546,
              "latitude": 35.1585232,
              "placeUrl": "https://map.naver.com/p/entry/place/1234567"
            }
            """;

    /** 카카오 로컬 응답. place_url을 그대로 전달한다. */
    static final String RESOLVE_KAKAO = """
            {
              "source": "KAKAO_LOCAL",
              "externalId": "26338954",
              "name": "구남로 카페",
              "category": "음식점 > 카페",
              "address": "부산 해운대구 구남로 1",
              "longitude": 129.1615,
              "latitude": 35.1620,
              "placeUrl": "https://place.map.kakao.com/26338954"
            }
            """;

    private PlaceOpenApiExamples() {
    }
}
