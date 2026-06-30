package com.server.external.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record KakaoLocalSearchResponse(
        List<Document> documents
) {

    public List<Document> documentsOrEmpty() {
        return documents == null ? List.of() : documents;
    }

    public record Document(
            String id,
            @JsonProperty("place_name")
            String placeName,
            @JsonProperty("address_name")
            String addressName,
            String x,
            String y,
            String distance,
            @JsonProperty("place_url")
            String placeUrl
    ) {
    }
}
