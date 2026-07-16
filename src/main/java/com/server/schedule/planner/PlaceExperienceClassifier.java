package com.server.schedule.planner;

import com.server.place.domain.Place;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PlaceExperienceClassifier {

    private PlaceExperienceClassifier() {
    }

    public static ExperienceProfile classify(Place place) {
        String contentTypeId = value(place.getContentTypeId());
        String text = (value(place.getName()) + " " + value(place.getCategory()))
                .toLowerCase(Locale.ROOT);
        boolean cafe = Objects.equals(contentTypeId, "39")
                && containsAny(text, "카페", "커피", "베이커리", "디저트", "찻집");
        boolean food = Objects.equals(contentTypeId, "39");
        boolean market = containsAny(text, "시장", "상가", "전통시장");
        boolean shopping = Objects.equals(contentTypeId, "38")
                || containsAny(text, "쇼핑", "백화점", "아울렛", "쇼핑몰");
        boolean exhibition = Objects.equals(contentTypeId, "14")
                || containsAny(text, "박물관", "미술관", "전시", "기념관");
        boolean history = containsAny(text, "역사", "유적", "문화재", "사찰", "성당", "교회");
        boolean event = Objects.equals(contentTypeId, "15")
                || containsAny(text, "축제", "공연", "행사", "페스티벌");
        boolean festival = containsAny(text, "축제", "페스티벌");
        boolean performance = containsAny(text, "공연", "콘서트", "뮤지컬", "불꽃");
        boolean nightView = containsAny(text, "야경", "나이트", "night view");
        boolean activity = Objects.equals(contentTypeId, "28")
                || containsAny(text, "체험", "레저", "요트", "서핑", "케이블카");
        boolean beach = containsAny(text, "해수욕장", "해변", "비치", "백사장");
        boolean coastal = beach || containsAny(text, "바다", "해안", "광안리", "송도", "송정", "영도", "기장");
        boolean coastalView = containsAny(text, "전망대", "스카이워크", "다릿돌", "등대") && coastal;
        boolean nature = containsAny(text, "숲", "둘레길", "산책로", "수목원", "생태", "등산",
                "금정산", "장산", "황령산", "승학산");
        boolean park = containsAny(text, "공원", "정원");
        boolean village = containsAny(text, "마을", "골목", "거리", "로드");
        boolean landmark = containsAny(text, "전망", "타워", "랜드마크", "광장");

        Set<PlaceIdentity> identities = EnumSet.noneOf(PlaceIdentity.class);
        Set<EnvironmentType> environments = EnumSet.noneOf(EnvironmentType.class);
        Set<ContentType> contents = EnumSet.noneOf(ContentType.class);
        Set<AtmosphereType> atmospheres = EnumSet.noneOf(AtmosphereType.class);
        Map<AvailableExperience, Integer> experiences = new LinkedHashMap<>();

        if (food) {
            identities.add(cafe ? PlaceIdentity.CAFE : PlaceIdentity.RESTAURANT);
            contents.add(ContentType.FOOD);
            experience(experiences, cafe ? AvailableExperience.CAFE_REST : AvailableExperience.FOOD, 100);
        }
        if (market) {
            identities.add(PlaceIdentity.MARKET);
            environments.add(EnvironmentType.URBAN);
            contents.add(ContentType.SHOPPING);
            atmospheres.add(AtmosphereType.LOCAL);
            atmospheres.add(AtmosphereType.LIVELY);
            experience(experiences, AvailableExperience.MARKET_BROWSING, 100);
        }
        if (shopping) {
            identities.add(PlaceIdentity.SHOPPING);
            environments.add(EnvironmentType.URBAN);
            contents.add(ContentType.SHOPPING);
            experience(experiences, AvailableExperience.SHOPPING, 100);
        }
        if (exhibition) {
            identities.add(PlaceIdentity.MUSEUM);
            environments.add(EnvironmentType.INDOOR);
            contents.add(ContentType.EXHIBITION);
            experience(experiences, AvailableExperience.EXHIBITION_VIEW, 100);
            experience(experiences, AvailableExperience.CULTURE_VIEW, 80);
        }
        if (history) {
            identities.add(PlaceIdentity.HERITAGE);
            contents.add(ContentType.HISTORY);
            experience(experiences, AvailableExperience.CULTURE_VIEW, 100);
        }
        if (event) {
            identities.add(PlaceIdentity.EVENT_VENUE);
            contents.add(ContentType.EVENT);
            if (festival) contents.add(ContentType.FESTIVAL);
            atmospheres.add(AtmosphereType.LIVELY);
            experience(experiences, AvailableExperience.EVENT_ATTENDANCE, 100);
        }
        if (performance) {
            contents.add(ContentType.PERFORMANCE);
            experience(experiences, AvailableExperience.PERFORMANCE_VIEW, 100);
        }
        if (nightView || containsAny(text, "불꽃", "야간")) {
            atmospheres.add(AtmosphereType.NIGHT_VIEW);
            experience(experiences, AvailableExperience.NIGHT_VIEW, 80);
        }
        if (activity) {
            identities.add(PlaceIdentity.ACTIVITY);
            contents.add(ContentType.ACTIVITY);
            experience(experiences, AvailableExperience.ACTIVITY, 100);
        }
        if (beach) {
            identities.add(PlaceIdentity.BEACH);
        }
        if (coastal) {
            environments.add(EnvironmentType.COASTAL);
            atmospheres.add(AtmosphereType.SCENIC);
            experience(experiences, AvailableExperience.SEA_VIEW, event ? 60 : 100);
            if (!event && beach) {
                experience(experiences, AvailableExperience.BEACH_WALK, 100);
            }
            experience(experiences, AvailableExperience.PHOTO, event ? 60 : 80);
        }
        if (coastalView || landmark) {
            identities.add(PlaceIdentity.LANDMARK);
            atmospheres.add(AtmosphereType.SCENIC);
            experience(experiences, AvailableExperience.SCENIC_VIEW, 100);
        }
        if (nature) {
            identities.add(PlaceIdentity.NATURE_TRAIL);
            environments.add(EnvironmentType.GREEN);
            atmospheres.add(AtmosphereType.QUIET);
            experience(experiences, AvailableExperience.NATURE_WALK, 100);
            experience(experiences, AvailableExperience.PHOTO, 70);
        }
        if (park) {
            identities.add(PlaceIdentity.PARK);
            environments.add(EnvironmentType.GREEN);
            atmospheres.add(AtmosphereType.QUIET);
            experience(experiences, AvailableExperience.PARK_REST, 100);
            experience(experiences, AvailableExperience.REST, 80);
        }
        if (village) {
            identities.add(PlaceIdentity.VILLAGE_STREET);
            environments.add(EnvironmentType.URBAN);
            atmospheres.add(AtmosphereType.LOCAL);
            experience(experiences, AvailableExperience.VILLAGE_WALK, 100);
            experience(experiences, AvailableExperience.PHOTO, 70);
        }

        ExperienceType type = primaryType(
                food, cafe, market, shopping, exhibition, history, event, activity,
                beach, coastalView, nature, park, village, landmark);
        SemanticGroup group = primarySemanticGroup(type, environments);
        if (identities.isEmpty()) identities.add(PlaceIdentity.OTHER);
        if (environments.isEmpty()) environments.add(EnvironmentType.OTHER);
        if (contents.isEmpty()) contents.add(ContentType.NONE);
        if (atmospheres.isEmpty()) atmospheres.add(AtmosphereType.OTHER);
        return new ExperienceProfile(type, group, identities, environments, experiences, contents, atmospheres);
    }

    public static int similarityPercent(ExperienceProfile left, ExperienceProfile right) {
        double identity = overlap(left.placeIdentities(), right.placeIdentities());
        double environment = overlap(left.environments(), right.environments());
        double experience = experienceOverlap(left.availableExperiences(), right.availableExperiences());
        double content = overlap(left.contents(), right.contents());
        return (int) Math.round((identity * 0.25 + environment * 0.30
                + experience * 0.30 + content * 0.15) * 100);
    }

    private static ExperienceType primaryType(
            boolean food,
            boolean cafe,
            boolean market,
            boolean shopping,
            boolean exhibition,
            boolean history,
            boolean event,
            boolean activity,
            boolean beach,
            boolean coastalView,
            boolean nature,
            boolean park,
            boolean village,
            boolean landmark
    ) {
        if (food) return cafe ? ExperienceType.CAFE_REST : ExperienceType.FOOD;
        if (market) return ExperienceType.MARKET_COMMERCE;
        if (shopping) return ExperienceType.SHOPPING;
        if (exhibition) return ExperienceType.EXHIBITION_MUSEUM;
        if (history) return ExperienceType.HISTORY_CULTURE;
        if (event) return ExperienceType.EVENT;
        if (activity) return ExperienceType.ACTIVITY;
        if (beach) return ExperienceType.BEACH_WALK;
        if (coastalView) return ExperienceType.COASTAL_VIEW;
        if (nature) return ExperienceType.NATURE_TRAIL;
        if (park) return ExperienceType.PARK_GREEN;
        if (village) return ExperienceType.VILLAGE_STREET_WALK;
        if (landmark) return ExperienceType.LANDMARK;
        return ExperienceType.OTHER;
    }

    private static SemanticGroup primarySemanticGroup(
            ExperienceType type,
            Set<EnvironmentType> environments
    ) {
        if (environments.contains(EnvironmentType.COASTAL)) return SemanticGroup.COASTAL_NATURE;
        if (environments.contains(EnvironmentType.GREEN)) return SemanticGroup.GREEN_NATURE;
        return switch (type) {
            case VILLAGE_STREET_WALK -> SemanticGroup.URBAN_WALK;
            case MARKET_COMMERCE, SHOPPING -> SemanticGroup.COMMERCE;
            case HISTORY_CULTURE, EXHIBITION_MUSEUM, EVENT -> SemanticGroup.CULTURE;
            case ACTIVITY -> SemanticGroup.ACTIVITY;
            case FOOD, CAFE_REST -> SemanticGroup.FOOD_REST;
            case LANDMARK -> SemanticGroup.LANDMARK;
            default -> SemanticGroup.OTHER;
        };
    }

    private static void experience(
            Map<AvailableExperience, Integer> experiences,
            AvailableExperience type,
            int contribution
    ) {
        experiences.merge(type, contribution, Math::max);
    }

    private static double overlap(Set<?> left, Set<?> right) {
        int intersection = 0;
        for (Object item : left) {
            if (right.contains(item)) intersection++;
        }
        int smallerSetSize = Math.min(left.size(), right.size());
        return smallerSetSize == 0 ? 0 : (double) intersection / smallerSetSize;
    }

    private static double experienceOverlap(
            Map<AvailableExperience, Integer> left,
            Map<AvailableExperience, Integer> right
    ) {
        int sharedContribution = 0;
        for (Map.Entry<AvailableExperience, Integer> entry : left.entrySet()) {
            Integer other = right.get(entry.getKey());
            if (other != null) sharedContribution += Math.min(entry.getValue(), other);
        }
        int smallerContribution = Math.min(
                left.values().stream().mapToInt(Integer::intValue).sum(),
                right.values().stream().mapToInt(Integer::intValue).sum());
        return smallerContribution == 0 ? 0 : (double) sharedContribution / smallerContribution;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) return true;
        }
        return false;
    }

    public enum ExperienceType {
        BEACH_WALK, COASTAL_VIEW, NATURE_TRAIL, PARK_GREEN, VILLAGE_STREET_WALK,
        MARKET_COMMERCE, HISTORY_CULTURE, EXHIBITION_MUSEUM, ACTIVITY, FOOD,
        CAFE_REST, EVENT, SHOPPING, LANDMARK, OTHER
    }

    public enum SemanticGroup {
        COASTAL_NATURE, GREEN_NATURE, URBAN_WALK, CULTURE, COMMERCE, ACTIVITY,
        FOOD_REST, LANDMARK, OTHER
    }

    public enum PlaceIdentity {
        BEACH, PARK, NATURE_TRAIL, VILLAGE_STREET, MARKET, MUSEUM, HERITAGE,
        ACTIVITY, RESTAURANT, CAFE, EVENT_VENUE, SHOPPING, LANDMARK, OTHER
    }

    public enum EnvironmentType {
        COASTAL, GREEN, URBAN, INDOOR, OTHER
    }

    public enum AvailableExperience {
        SEA_VIEW, BEACH_WALK, NATURE_WALK, PARK_REST, VILLAGE_WALK, MARKET_BROWSING,
        CULTURE_VIEW, EXHIBITION_VIEW, EVENT_ATTENDANCE, PERFORMANCE_VIEW, NIGHT_VIEW,
        SCENIC_VIEW, PHOTO, FOOD, CAFE_REST, SHOPPING, ACTIVITY, REST
    }

    public enum ContentType {
        FESTIVAL, EVENT, PERFORMANCE, EXHIBITION, HISTORY, FOOD, SHOPPING, ACTIVITY, NONE
    }

    public enum AtmosphereType {
        NIGHT_VIEW, LIVELY, QUIET, LOCAL, SCENIC, OTHER
    }

    public record ExperienceProfile(
            ExperienceType type,
            SemanticGroup semanticGroup,
            Set<PlaceIdentity> placeIdentities,
            Set<EnvironmentType> environments,
            Map<AvailableExperience, Integer> availableExperiences,
            Set<ContentType> contents,
            Set<AtmosphereType> atmospheres
    ) {
        public ExperienceProfile {
            placeIdentities = Set.copyOf(placeIdentities);
            environments = Set.copyOf(environments);
            availableExperiences = Map.copyOf(availableExperiences);
            contents = Set.copyOf(contents);
            atmospheres = Set.copyOf(atmospheres);
        }

        public int contribution(AvailableExperience experience) {
            return availableExperiences.getOrDefault(experience, 0);
        }
    }
}
