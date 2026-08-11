package com.server.place.support;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PlaceCategoryLabelResolver {

    private static final Map<String, String> CATEGORY_PREFIX_LABELS = categoryLabels();
    private static final Map<String, String> CONTENT_TYPE_LABELS = Map.of(
            "12", "관광지",
            "14", "문화시설",
            "15", "축제·공연",
            "28", "레포츠",
            "32", "숙박",
            "38", "쇼핑",
            "39", "음식점"
    );

    private static final String DEFAULT_LABEL = "관광지";

    private PlaceCategoryLabelResolver() {
    }

    /**
     * TourAPI 분류코드는 사람이 읽을 라벨로 바꾸고, 외부 제공자의 자유 형식 카테고리는
     * 그대로 쓴다.
     *
     * <p>TourAPI cat1은 A(관광지·문화·축제·레포츠) 외에 B(숙박), C(추천코스)도 쓴다.
     * 예전에는 A로 시작하는 코드만 라벨로 바꾸고 나머지는 원본을 반환해서, 숙박 장소의
     * categoryLabel이 "B02011100" 같은 코드 그대로 화면에 노출됐다.
     */
    public static String resolve(String category, String contentTypeId) {
        if (category != null && !category.isBlank()) {
            if (!isTourApiCategoryCode(category)) return category;
            for (Map.Entry<String, String> entry : CATEGORY_PREFIX_LABELS.entrySet()) {
                if (category.startsWith(entry.getKey())) return entry.getValue();
            }
        }
        if (contentTypeId == null) return DEFAULT_LABEL;
        return CONTENT_TYPE_LABELS.getOrDefault(contentTypeId, DEFAULT_LABEL);
    }

    private static boolean isTourApiCategoryCode(String category) {
        return category.matches("[A-C]\\d+");
    }

    private static Map<String, String> categoryLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("A0101", "자연 관광지");
        labels.put("A0102", "관광 자원");
        labels.put("A0201", "역사 관광지");
        labels.put("A0202", "휴양 관광지");
        labels.put("A0203", "체험 관광지");
        labels.put("A0207", "축제·공연");
        labels.put("A0208", "공연·행사");
        labels.put("A0302", "레포츠");
        labels.put("A0401", "쇼핑");
        labels.put("A0502", "음식점");
        return Map.copyOf(labels);
    }
}
