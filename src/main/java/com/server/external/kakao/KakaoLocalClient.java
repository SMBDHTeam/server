package com.server.external.kakao;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import java.math.BigDecimal;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class KakaoLocalClient {

    private static final String AUTHORIZATION_PREFIX = "KakaoAK ";
    private static final String CONVENIENCE_STORE_CATEGORY_CODE = "CS2";
    private static final int DEFAULT_CATEGORY_SIZE = 15;

    private final RestClient restClient;
    private final KakaoLocalProperties properties;

    public KakaoLocalClient(RestClient kakaoLocalRestClient, KakaoLocalProperties properties) {
        this.restClient = kakaoLocalRestClient;
        this.properties = properties;
    }

    public KakaoLocalSearchResponse searchKeyword(String keyword, int size) {
        return execute(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/local/search/keyword.json")
                        .queryParam("query", keyword)
                        .queryParam("size", size)
                        .build())
                .header("Authorization", authorizationHeader())
                .retrieve()
                .body(KakaoLocalSearchResponse.class),
                () -> new KakaoLocalSearchResponse(null));
    }

    /**
     * 좌표 주변에서 키워드로 찾는다. 가까운 순으로 받는다.
     *
     * <p>같은 이름이 부산 여러 곳에 있을 수 있어, 우리 장소와 같은 곳을 고를 때 좌표로 좁힌다.
     */
    public KakaoLocalSearchResponse searchKeywordNear(
            String keyword,
            BigDecimal longitude,
            BigDecimal latitude,
            int radius,
            int size
    ) {
        return execute(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/local/search/keyword.json")
                        .queryParam("query", keyword)
                        .queryParam("x", longitude)
                        .queryParam("y", latitude)
                        .queryParam("radius", radius)
                        .queryParam("sort", "distance")
                        .queryParam("size", size)
                        .build())
                .header("Authorization", authorizationHeader())
                .retrieve()
                .body(KakaoLocalSearchResponse.class),
                () -> new KakaoLocalSearchResponse(null));
    }

    public KakaoLocalSearchResponse searchConvenienceStores(
            BigDecimal longitude,
            BigDecimal latitude,
            int radius
    ) {
        return execute(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/local/search/category.json")
                        .queryParam("category_group_code", CONVENIENCE_STORE_CATEGORY_CODE)
                        .queryParam("x", longitude)
                        .queryParam("y", latitude)
                        .queryParam("radius", radius)
                        .queryParam("size", DEFAULT_CATEGORY_SIZE)
                        .build())
                .header("Authorization", authorizationHeader())
                .retrieve()
                .body(KakaoLocalSearchResponse.class),
                () -> new KakaoLocalSearchResponse(null));
    }

    public KakaoLocalRegionCodeResponse searchRegionCode(
            BigDecimal longitude,
            BigDecimal latitude
    ) {
        return execute(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/local/geo/coord2regioncode.json")
                        .queryParam("x", longitude)
                        .queryParam("y", latitude)
                        .build())
                .header("Authorization", authorizationHeader())
                .retrieve()
                .body(KakaoLocalRegionCodeResponse.class),
                () -> new KakaoLocalRegionCodeResponse(null));
    }

    private <T> T execute(KakaoRequest<T> request, Supplier<T> emptyResponse) {
        if (properties.restApiKey().isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
        }
        try {
            T response = request.get();
            return response == null ? emptyResponse.get() : response;
        } catch (RestClientResponseException | ResourceAccessException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    private String authorizationHeader() {
        return AUTHORIZATION_PREFIX + properties.restApiKey();
    }

    @FunctionalInterface
    private interface KakaoRequest<T> {
        T get();
    }
}
