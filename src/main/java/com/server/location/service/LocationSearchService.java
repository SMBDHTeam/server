package com.server.location.service;

import com.server.external.kakao.KakaoLocalClient;
import com.server.external.kakao.KakaoLocalSearchResponse;
import com.server.location.dto.LocationSearchResponse;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class LocationSearchService {

    private static final String SOURCE = "KAKAO_LOCAL";

    private final KakaoLocalClient kakaoLocalClient;

    public LocationSearchService(KakaoLocalClient kakaoLocalClient) {
        this.kakaoLocalClient = kakaoLocalClient;
    }

    public LocationSearchResponse search(String keyword, int size) {
        KakaoLocalSearchResponse response = kakaoLocalClient.searchKeyword(keyword, size);
        return new LocationSearchResponse(response.documentsOrEmpty()
                .stream()
                .map(this::toItem)
                .toList());
    }

    private LocationSearchResponse.Item toItem(KakaoLocalSearchResponse.Document document) {
        return new LocationSearchResponse.Item(
                document.placeName(),
                document.addressName(),
                new BigDecimal(document.x()),
                new BigDecimal(document.y()),
                document.id(),
                SOURCE
        );
    }
}
