package com.server.place.ingestion;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.place-ingestion.tour-api")
public record TourApiPlaceIngestionProperties(
        boolean enabled,
        String areaCode,
        List<String> contentTypeIds,
        int pageSize,
        int maxPages,
        int maxRequestsPerDay,
        /**
         * 상세 보강(detailCommon2 + detailIntro2 + detailImage2) 수행 여부.
         *
         * <p>보강은 장소당 TourAPI를 3회 호출해 소개글·운영정보·이미지를 채운다.
         * 이 값들은 일정 생성에 쓰이지 않고 장소 상세 화면 표시에만 쓰인다.
         * 상세 화면을 외부 지도 서비스로 넘기면 보강이 불필요해지고, 끄면 같은
         * 일일 호출 예산으로 약 3배 많은 장소를 발견할 수 있다.
         */
        boolean enrichmentEnabled
) {

    public TourApiPlaceIngestionProperties {
        if (areaCode == null || areaCode.isBlank()) {
            areaCode = "6";
        }
        if (contentTypeIds == null || contentTypeIds.isEmpty()) {
            contentTypeIds = List.of("12", "14", "15", "28", "32", "38", "39");
        } else {
            contentTypeIds = List.copyOf(contentTypeIds);
        }
        if (pageSize <= 0) {
            pageSize = 100;
        }
        if (maxPages <= 0) {
            maxPages = 1;
        }
        if (maxRequestsPerDay <= 0 || maxRequestsPerDay > 1000) {
            maxRequestsPerDay = 900;
        }
    }
}
