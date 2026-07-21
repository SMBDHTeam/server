package com.server.place.support;

import java.util.Arrays;
import java.util.Optional;

public enum TourApiTheme {
    FOOD("THEME_FOOD", "맛집"),
    NATURE("THEME_NATURE", "자연"),
    CULTURE("THEME_CULTURE", "문화·역사"),
    ACTIVITY("THEME_ACTIVITY", "체험·액티비티"),
    SHOPPING("THEME_SHOPPING", "쇼핑"),
    HEALING("THEME_HEALING", "휴식");

    private final String answerId;
    private final String label;

    TourApiTheme(String answerId, String label) {
        this.answerId = answerId;
        this.label = label;
    }

    public String answerId() {
        return answerId;
    }

    public String label() {
        return label;
    }

    public static Optional<TourApiTheme> fromAnswerId(String answerId) {
        return Arrays.stream(values())
                .filter(theme -> theme.answerId.equals(answerId))
                .findFirst();
    }
}
