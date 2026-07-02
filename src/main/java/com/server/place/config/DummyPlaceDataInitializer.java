package com.server.place.config;

import com.server.place.domain.Place;
import com.server.place.repository.PlaceRepository;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DummyPlaceDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DummyPlaceDataInitializer.class);

    @Bean
    @ConditionalOnProperty(prefix = "app.seed.dummy-place", name = "enabled", havingValue = "true")
    ApplicationRunner seedDummyPlace(PlaceRepository placeRepository) {
        return args -> {
            if (placeRepository.count() > 0) {
                return;
            }

            Place place = new Place(
                    "TOUR_API",
                    "DUMMY_BUSAN_STATION",
                    "12",
                    "부산역 더미 장소",
                    "관광지",
                    "부산 동구 중앙대로 206",
                    new BigDecimal("129.04154985"),
                    new BigDecimal("35.11520341"),
                    null
            );
            Place savedPlace = placeRepository.save(place);
            log.info("Seeded dummy place id={}", savedPlace.getId());
        };
    }
}
