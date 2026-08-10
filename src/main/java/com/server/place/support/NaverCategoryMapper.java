package com.server.place.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a Naver Local category string onto the TourAPI content type our planner already
 * understands.
 *
 * <p>Naver returns free-form Korean categories such as {@code 음식점>한식>육류,고기요리} and no
 * content type. Without one, the schedule layer falls back
 * to a flat dwell time and {@link TourApiThemeMapper}'s content type rules never fire, so a place
 * the traveller picked themselves would be planned worse than an ingested one. Deriving a content
 * type at registration time keeps both paths identical.
 */
public final class NaverCategoryMapper {

    private static final String DEFAULT_CONTENT_TYPE_ID = "12";
    private static final Map<String, List<String>> CONTENT_TYPE_KEYWORDS = contentTypeKeywords();

    private NaverCategoryMapper() {
    }

    public static String contentTypeId(String category) {
        if (category == null || category.isBlank()) {
            return DEFAULT_CONTENT_TYPE_ID;
        }
        String normalized = category.replaceAll("\\s+", "");
        for (Map.Entry<String, List<String>> entry : CONTENT_TYPE_KEYWORDS.entrySet()) {
            if (entry.getValue().stream().anyMatch(normalized::contains)) {
                return entry.getKey();
            }
        }
        return DEFAULT_CONTENT_TYPE_ID;
    }

    /** Iteration order matters: the first matching group wins, so keep the specific ones first. */
    private static Map<String, List<String>> contentTypeKeywords() {
        Map<String, List<String>> keywords = new LinkedHashMap<>();
        keywords.put("39", List.of("음식점", "카페", "디저트", "베이커리", "술집", "주점"));
        keywords.put("15", List.of("축제", "공연", "행사", "전시회", "연극", "뮤지컬", "콘서트"));
        keywords.put("14", List.of("박물관", "미술관", "문화", "도서관", "기념관", "전시관", "영화관"));
        keywords.put("32", List.of("숙박", "호텔", "펜션", "게스트하우스", "모텔", "리조트"));
        keywords.put("38", List.of("쇼핑", "시장", "백화점", "마트", "아울렛", "면세점", "상가"));
        keywords.put("28", List.of("레포츠", "스포츠", "체험", "액티비티", "캠핑", "수상레저", "골프"));
        return keywords;
    }
}
