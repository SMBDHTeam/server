package com.server.external.kakao;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import java.math.BigDecimal;
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
                .body(KakaoLocalSearchResponse.class));
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
                .body(KakaoLocalSearchResponse.class));
    }

    private KakaoLocalSearchResponse execute(KakaoRequest request) {
        if (properties.restApiKey().isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
        }
        try {
            KakaoLocalSearchResponse response = request.get();
            return response == null ? new KakaoLocalSearchResponse(null) : response;
        } catch (RestClientResponseException | ResourceAccessException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    private String authorizationHeader() {
        return AUTHORIZATION_PREFIX + properties.restApiKey();
    }

    @FunctionalInterface
    private interface KakaoRequest {
        KakaoLocalSearchResponse get();
    }
}
