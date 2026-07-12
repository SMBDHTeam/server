package com.server.place.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.place-ingestion.tour-api.scheduler",
        name = "enabled",
        havingValue = "true"
)
public class TourApiPlaceIngestionScheduler {
    private static final Logger log = LoggerFactory.getLogger(TourApiPlaceIngestionScheduler.class);
    private final TourApiPlaceIngestionService ingestionService;

    public TourApiPlaceIngestionScheduler(TourApiPlaceIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Scheduled(
            cron = "${app.place-ingestion.tour-api.scheduler.cron}",
            zone = "Asia/Seoul"
    )
    public void ingestDaily() {
        try{
            TourApiPlaceIngestionResult result = ingestionService.ingestConfigured();
            log.info("Scheduled TourAPI place ingestion finished. fetched={}, saved={}, skipped={}",
                    result.fetched(), result.saved(), result.skipped());
        } catch (RuntimeException exception){
            log.error("Scheduled TourAPI place ingestion failed. Next run will continue.", exception);
        }
    }
}

