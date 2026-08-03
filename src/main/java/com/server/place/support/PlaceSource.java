package com.server.place.support;

import java.util.Set;

/**
 * Distinguishes places we ingested ourselves from places a traveller registered on the fly
 * through {@code POST /places/resolve}.
 *
 * <p>User registered rows are created from an external search result, so they carry no operating
 * hours, images or curated detail. They are legitimate as an explicitly chosen must-visit place,
 * but they must not leak into the automatic recommendation pool: one traveller registering a
 * neighbourhood cafe would otherwise start proposing it to everyone else.
 */
public final class PlaceSource {

    public static final String KAKAO_LOCAL = "KAKAO_LOCAL";
    public static final String NAVER_LOCAL = "NAVER_LOCAL";

    private static final Set<String> USER_REGISTERED = Set.of(KAKAO_LOCAL, NAVER_LOCAL);

    private PlaceSource() {
    }

    /** The sources {@code POST /places/resolve} is allowed to create. */
    public static Set<String> userRegistered() {
        return USER_REGISTERED;
    }

    public static boolean isUserRegistered(String source) {
        return source != null && USER_REGISTERED.contains(source);
    }
}
