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

    private PlaceCategoryLabelResolver() {
    }

    public static String resolve(String category, String contentTypeId) {
        if (category != null && !category.isBlank()) {
            if (!category.matches("A\\d+")) return category;
            for (Map.Entry<String, String> entry : CATEGORY_PREFIX_LABELS.entrySet()) {
                if (category.startsWith(entry.getKey())) return entry.getValue();
            }
        }
        return CONTENT_TYPE_LABELS.getOrDefault(contentTypeId, "관광지");
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
