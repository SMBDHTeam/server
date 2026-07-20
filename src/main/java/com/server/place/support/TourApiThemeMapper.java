package com.server.place.support;

import com.server.place.domain.Place;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TourApiThemeMapper {

    private static final Map<String, List<TourApiTheme>> CONTENT_TYPE_RULES = Map.of(
            "12", List.of(TourApiTheme.NATURE),
            "14", List.of(TourApiTheme.CULTURE),
            "28", List.of(TourApiTheme.ACTIVITY),
            "32", List.of(TourApiTheme.HEALING, TourApiTheme.NATURE),
            "38", List.of(TourApiTheme.SHOPPING),
            "39", List.of(TourApiTheme.FOOD)
    );

    private static final Map<TourApiTheme, List<String>> KEYWORD_RULES = new LinkedHashMap<>();

    static {
        KEYWORD_RULES.put(TourApiTheme.FOOD,
                List.of("카페", "브런치", "디저트", "커피", "맛집", "국밥", "횟집", "밀면", "돼지국밥",
                        "분식", "식당", "해산물"));
        KEYWORD_RULES.put(TourApiTheme.NATURE,
                List.of("해변", "해수욕장", "공원", "산", "숲", "수목원", "바다", "산책로"));
        KEYWORD_RULES.put(TourApiTheme.HEALING,
                List.of("산책", "휴식", "힐링", "조용", "여유", "온천", "사우나"));
        KEYWORD_RULES.put(TourApiTheme.CULTURE,
                List.of("박물관", "미술관", "전시", "역사", "전통", "공연", "문화", "기념관", "유적"));
        KEYWORD_RULES.put(TourApiTheme.ACTIVITY,
                List.of("체험", "레저", "테마파크", "액티비티", "케이블카", "요트", "서핑"));
        KEYWORD_RULES.put(TourApiTheme.SHOPPING,
                List.of("시장", "쇼핑", "기념품", "몰", "상가", "아울렛"));
    }

    private TourApiThemeMapper() {
    }

    public static Optional<TourApiTheme> primaryTheme(Place place) {
        return primaryTheme(
                place.getContentTypeId(),
                place.getName(),
                place.getCategory(),
                place.getAddress(),
                place.getDetail() != null ? place.getDetail().getOverview() : null
        );
    }

    public static Optional<TourApiTheme> primaryTheme(
            String contentTypeId,
            String title,
            String category,
            String description
    ) {
        List<TourApiTheme> themes = themes(contentTypeId, title, category, description, null);
        if (themes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(themes.get(0));
    }

    public static Optional<TourApiTheme> primaryTheme(
            String contentTypeId,
            String title,
            String category,
            String description,
            String overview
    ) {
        List<TourApiTheme> themes = themes(contentTypeId, title, category, description, overview);
        if (themes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(themes.get(0));
    }

    public static List<TourApiTheme> themes(Place place) {
        return themes(
                place.getContentTypeId(),
                place.getName(),
                place.getCategory(),
                place.getAddress(),
                place.getDetail() != null ? place.getDetail().getOverview() : null
        );
    }

    public static List<TourApiTheme> themes(
            String contentTypeId,
            String title,
            String category,
            String description
    ) {
        return themes(contentTypeId, title, category, description, null);
    }

    public static List<TourApiTheme> themes(
            String contentTypeId,
            String title,
            String category,
            String description,
            String overview
    ) {
        Set<TourApiTheme> ordered = new LinkedHashSet<>();
        ordered.addAll(CONTENT_TYPE_RULES.getOrDefault(blankToEmpty(contentTypeId), List.of()));

        String text = normalizeText(title, category, description, overview);
        for (Map.Entry<TourApiTheme, List<String>> entry : KEYWORD_RULES.entrySet()) {
            if (containsAny(text, entry.getValue())) {
                ordered.add(entry.getKey());
            }
        }

        return List.copyOf(ordered);
    }

    public static boolean matchesTheme(Place place, String answerId) {
        Optional<TourApiTheme> theme = TourApiTheme.fromAnswerId(answerId);
        if (theme.isEmpty()) {
            return false;
        }
        List<TourApiTheme> themes = themes(place);
        if (theme.get() == TourApiTheme.HEALING) {
            return themes.contains(TourApiTheme.HEALING)
                    || themes.contains(TourApiTheme.NATURE)
                    || themes.contains(TourApiTheme.FOOD);
        }
        return themes.contains(theme.get());
    }

    public static boolean matchesThemeText(String answerId, String... values) {
        Optional<TourApiTheme> theme = TourApiTheme.fromAnswerId(answerId);
        if (theme.isEmpty()) {
            return false;
        }
        String text = normalizeText(values);
        if (theme.get() == TourApiTheme.HEALING) {
            return containsAny(text, KEYWORD_RULES.get(TourApiTheme.HEALING))
                    || containsAny(text, List.of("공원", "숲", "산책", "수목원", "카페", "온천"));
        }
        return containsAny(text, KEYWORD_RULES.getOrDefault(theme.get(), List.of()));
    }

    private static String normalizeText(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                parts.add(value.strip());
            }
        }
        return String.join(" ", parts).toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, List<String> keywords) {
        return keywords.stream().anyMatch(value::contains);
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
