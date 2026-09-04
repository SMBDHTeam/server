package com.server.external.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record KakaoLocalRegionCodeResponse(
        List<Document> documents
) {

    public List<Document> documentsOrEmpty() {
        return documents == null ? List.of() : documents;
    }

    public record Document(
            @JsonProperty("region_type")
            String regionType,
            @JsonProperty("address_name")
            String addressName,
            @JsonProperty("region_1depth_name")
            String region1DepthName,
            @JsonProperty("region_2depth_name")
            String region2DepthName,
            @JsonProperty("region_3depth_name")
            String region3DepthName,
            @JsonProperty("region_4depth_name")
            String region4DepthName,
            String code,
            Double x,
            Double y
    ) {
    }
}
