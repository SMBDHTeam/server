package com.server.place.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.external.kakao.KakaoLocalClient;
import com.server.external.kakao.KakaoLocalSearchResponse;
import com.server.place.domain.Place;
import com.server.place.dto.PlaceKakaoLinkResponse;
import com.server.place.repository.PlaceRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 장소를 카카오맵 페이지로 잇는다.
 *
 * <p>장소 상세는 우리 DB 대신 카카오맵 장소 페이지를 보여준다. 후기·사진·영업 정보가 계속 갱신되기
 * 때문이다. 문제는 장소 대부분이 TourAPI·네이버에서 와서 카카오 장소 ID 가 없다는 점이다.
 * 이름과 좌표로 카카오 지역 검색을 해 같은 장소를 찾는다.
 *
 * <p>찾지 못해도 오류로 끝내지 않는다. 이름 검색 결과 페이지를 주면 사용자가 목록에서 고를 수 있다.
 * 카카오 호출이 실패해도 같다. 장소를 보려는 사람에게 카카오 장애를 보여줄 이유가 없다.
 *
 * <p>트랜잭션을 걸지 않는다. 외부 호출을 기다리는 동안 DB 커넥션을 쥐고 있으면 안 된다.
 */
@Service
public class PlaceKakaoLinkService {

    private static final Logger log = LoggerFactory.getLogger(PlaceKakaoLinkService.class);

    /** 같은 장소로 볼 거리. TourAPI·네이버·카카오 좌표는 입구·중심 차이로 수십 미터 벌어진다. */
    static final int MATCH_RADIUS_METERS = 300;
    /** 카카오 최대치. 관광지 주변은 이름을 딴 가게가 많아 진짜 장소가 뒤로 밀린다. */
    private static final int SEARCH_SIZE = 15;
    private static final String KAKAO_SOURCE = "KAKAO_LOCAL";
    private static final String SEARCH_PAGE = "https://m.map.kakao.com/actions/searchView?q=";

    private final PlaceRepository placeRepository;
    private final KakaoLocalClient kakaoLocalClient;

    public PlaceKakaoLinkService(PlaceRepository placeRepository, KakaoLocalClient kakaoLocalClient) {
        this.placeRepository = placeRepository;
        this.kakaoLocalClient = kakaoLocalClient;
    }

    public PlaceKakaoLinkResponse getLink(Long placeId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        // 장소 상세와 같은 규칙이다. 가린 장소는 없는 것으로 다룬다.
        if (place.isHidden()) {
            throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
        }

        if (KAKAO_SOURCE.equals(place.getSource()) && hasText(place.getExternalContentId())) {
            return PlaceKakaoLinkResponse.matched(place.getId(), place.getExternalContentId());
        }
        return findKakaoPlaceId(place)
                .map(kakaoPlaceId -> PlaceKakaoLinkResponse.matched(place.getId(), kakaoPlaceId))
                .orElseGet(() -> PlaceKakaoLinkResponse.search(place.getId(), searchUrl(place.getName())));
    }

    private Optional<String> findKakaoPlaceId(Place place) {
        if (place.getLongitude() == null || place.getLatitude() == null || !hasText(place.getName())) {
            return Optional.empty();
        }
        try {
            // 가까운 순으로 받으므로 이름이 같은 첫 결과가 가장 가까운 같은 장소다.
            return kakaoLocalClient.searchKeywordNear(
                            searchKeyword(place.getName()), place.getLongitude(), place.getLatitude(),
                            MATCH_RADIUS_METERS, SEARCH_SIZE)
                    .documentsOrEmpty().stream()
                    .filter(document -> hasText(document.id()) && sameName(place.getName(), document.placeName()))
                    .findFirst()
                    .map(KakaoLocalSearchResponse.Document::id);
        } catch (BusinessException exception) {
            log.warn("Kakao place lookup failed, falling back to search page. placeId={}", place.getId(), exception);
            return Optional.empty();
        }
    }

    /**
     * 표기만 다른 같은 이름인지. "국제시장 먹자골목"과 "국제시장먹자골목", "감천사(부산)"과 "감천사",
     * "부산 송도해상케이블카"와 "송도해상케이블카"를 같게 본다.
     *
     * <p>한쪽이 다른 쪽을 품는 경우는 같게 보지 않는다. 관광지 주변 가게가 "탐앤탐스 부산송도해수욕장점"처럼
     * 이름을 따서, 품는 것을 허용하면 가게로 이어졌다. 잘못된 장소보다 검색 결과가 낫다.
     */
    static boolean sameName(String ours, String theirs) {
        String a = normalize(ours);
        return !a.isEmpty() && a.equals(normalize(theirs));
    }

    private static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("\\([^)]*\\)", "")
                .replaceAll("[\\s·.,'\"\\-_]", "")
                .replaceFirst("^(부산광역시|부산시|부산)(?=.)", "")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * 카카오에 보낼 검색어. TourAPI 이름은 "동래온천길(온천천 카페거리)"처럼 괄호 설명이 붙는데,
     * 그대로 보내면 카카오가 아무것도 찾지 못한다.
     */
    static String searchKeyword(String name) {
        if (name == null) {
            return "";
        }
        String stripped = name.replaceAll("\\([^)]*\\)", " ").replaceAll("\\s+", " ").trim();
        return stripped.isEmpty() ? name.trim() : stripped;
    }

    /** 부산 밖 같은 이름이 먼저 나오지 않게 지역을 붙인다. */
    static String searchUrl(String name) {
        String keyword = searchKeyword(name);
        String query = keyword.contains("부산") ? keyword : "부산 " + keyword;
        return SEARCH_PAGE + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
