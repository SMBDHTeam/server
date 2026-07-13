package com.server.place.config;

import com.server.place.domain.Place;
import com.server.place.repository.PlaceRepository;
import java.math.BigDecimal;
import java.util.List;
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

            List<Place> places = List.of(
                    place("GAMCHEON", "14", "감천문화마을", "문화시설", "부산 사하구 감내2로 203", "129.0106", "35.0974"),
                    place("SONGDO_BEACH", "12", "송도해수욕장", "관광지", "부산 서구 송도해변로 100", "129.0172", "35.0770"),
                    place("JAGALCHI", "38", "자갈치시장", "쇼핑", "부산 중구 자갈치해안로 52", "129.0305", "35.0967"),
                    place("GWANGALLI", "12", "광안리해수욕장", "관광지", "부산 수영구 광안해변로 219", "129.1186", "35.1532"),
                    place("BUSAN_MUSEUM", "14", "부산박물관", "문화시설", "부산 남구 유엔평화로 63", "129.0840", "35.1296"),
                    place("HAEUNDAE_BEACH", "12", "해운대해수욕장", "관광지", "부산 해운대구 해운대해변로 264", "129.1604", "35.1587"),
                    place("DALMAJI", "12", "달맞이길 전망대", "관광지", "부산 해운대구 달맞이길 190", "129.1775", "35.1578"),
                    place("GUKJE_MARKET", "38", "국제시장", "쇼핑", "부산 중구 신창동4가", "129.0286", "35.1025")
            );
            placeRepository.saveAll(places);
            log.info("Seeded {} dummy places", places.size());
        };
    }

    private Place place(
            String externalContentId,
            String contentTypeId,
            String name,
            String category,
            String address,
            String longitude,
            String latitude
    ) {
        return new Place(
                "LOCAL_FIXTURE",
                externalContentId,
                contentTypeId,
                name,
                category,
                address,
                new BigDecimal(longitude),
                new BigDecimal(latitude),
                null
        );
    }
}
