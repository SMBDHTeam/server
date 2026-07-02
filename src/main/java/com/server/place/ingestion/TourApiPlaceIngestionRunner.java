package com.server.place.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TourApiPlaceIngestionRunner {

    private static final Logger log = LoggerFactory.getLogger(TourApiPlaceIngestionRunner.class);

    @Bean
    @ConditionalOnProperty(prefix = "app.place-ingestion.tour-api", name = "enabled", havingValue = "true")
    ApplicationRunner ingestTourApiPlaces(TourApiPlaceIngestionService ingestionService) {
        return args -> {
            try {
                TourApiPlaceIngestionResult result = ingestionService.ingestConfigured();
                log.info(
                        "TourAPI place ingestion finished. fetched={}, saved={}, skipped={}",
                        result.fetched(),
                        result.saved(),
                        result.skipped()
                );
            } catch (RuntimeException exception) {
                log.error("TourAPI place ingestion failed. Server startup will continue.", exception);
            }
        };
    }
}
