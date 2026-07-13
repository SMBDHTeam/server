package com.server.place.ingestion;

import com.server.common.error.BusinessException;
import com.server.external.tourapi.TourApiClient;
import com.server.external.tourapi.TourApiPlaceDetailResponse;
import com.server.external.tourapi.TourApiPlaceImageResponse;
import com.server.external.tourapi.TourApiPlaceIntroResponse;
import com.server.external.tourapi.TourApiPlaceListResponse;
import com.server.place.ingestion.TourApiIngestionLock.Lease;
import com.server.place.ingestion.TourApiPlaceWriter.PlaceSyncCandidate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TourApiPlaceIngestionService {

    private static final Logger log = LoggerFactory.getLogger(TourApiPlaceIngestionService.class);
    private static final DateTimeFormatter SOURCE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int ENRICHMENT_REQUESTS = 3;
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final TourApiClient tourApiClient;
    private final TourApiPlaceWriter placeWriter;
    private final TourApiPlaceIngestionProperties properties;
    private final TourApiIngestionLock ingestionLock;
    private final TourApiDailyRequestQuota requestQuota;

    public TourApiPlaceIngestionService(
            TourApiClient tourApiClient,
            TourApiPlaceWriter placeWriter,
            TourApiPlaceIngestionProperties properties,
            TourApiIngestionLock ingestionLock,
            TourApiDailyRequestQuota requestQuota
    ) {
        this.tourApiClient = tourApiClient;
        this.placeWriter = placeWriter;
        this.properties = properties;
        this.ingestionLock = ingestionLock;
        this.requestQuota = requestQuota;
    }

    public TourApiPlaceIngestionResult ingestConfigured() {
        Optional<Lease> lease = ingestionLock.tryAcquire();
        if (lease.isEmpty()) {
            return TourApiPlaceIngestionResult.locked();
        }
        try {
            return ingestWithLock();
        } finally {
            ingestionLock.release(lease.get());
        }
    }

    private TourApiPlaceIngestionResult ingestWithLock() {
        Counters counters = new Counters();
        List<PlaceSyncCandidate> enrichmentCandidates = discoverPlaces(counters);
        enrichPlaces(enrichmentCandidates, counters);
        return counters.toResult();
    }

    private List<PlaceSyncCandidate> discoverPlaces(Counters counters) {
        List<PlaceSyncCandidate> candidates = new ArrayList<>();
        LocalDateTime seenAt = LocalDateTime.now(KOREA_ZONE);
        for (String contentTypeId : properties.contentTypeIds()) {
            int pageNo = 1;
            while (pageNo <= properties.maxPages() && reserveRequests(1, counters)) {
                TourApiPlaceListResponse page = tourApiClient.searchPlaces(
                        properties.areaCode(),
                        contentTypeId,
                        pageNo,
                        properties.pageSize()
                );
                counters.fetched += page.items().size();
                for (TourApiPlaceListResponse.Item item : page.items()) {
                    Optional<PlaceDiscoveryItem> discoveryItem = toDiscoveryItem(contentTypeId, item);
                    if (discoveryItem.isEmpty()) {
                        counters.skipped++;
                        continue;
                    }
                    PlaceSyncCandidate candidate = placeWriter.discover(discoveryItem.get(), seenAt);
                    if (candidate.created()) {
                        counters.discovered++;
                    }
                    if (candidate.enrichmentRequired()) {
                        candidates.add(candidate);
                    } else {
                        counters.unchanged++;
                    }
                }
                if (page.items().isEmpty() || pageNo * properties.pageSize() >= page.totalCount()) {
                    break;
                }
                pageNo++;
            }
        }
        return candidates;
    }

    private void enrichPlaces(
            List<PlaceSyncCandidate> candidates,
            Counters counters
    ) {
        for (PlaceSyncCandidate candidate : candidates) {
            if (!reserveRequests(ENRICHMENT_REQUESTS, counters)) {
                counters.pending++;
                continue;
            }
            try {
                PlaceEnrichmentItem item = loadEnrichment(candidate);
                placeWriter.completeEnrichment(
                        candidate.externalContentId(),
                        item,
                        LocalDateTime.now(KOREA_ZONE)
                );
                counters.enriched++;
            } catch (RuntimeException exception) {
                String errorCode = errorCode(exception);
                placeWriter.markFailed(
                        candidate.externalContentId(),
                        errorCode,
                        LocalDateTime.now(KOREA_ZONE)
                );
                counters.failed++;
                log.warn(
                        "TourAPI place enrichment failed. contentId={}, errorCode={}",
                        candidate.externalContentId(),
                        errorCode
                );
            }
        }
    }

    private boolean reserveRequests(int requests, Counters counters) {
        boolean reserved = requestQuota.tryReserve(requests, properties.maxRequestsPerDay());
        if (reserved) {
            counters.apiRequests += requests;
        }
        return reserved;
    }

    private PlaceEnrichmentItem loadEnrichment(PlaceSyncCandidate candidate) {
        TourApiPlaceDetailResponse detail = tourApiClient.getCommonDetail(candidate.externalContentId());
        TourApiPlaceIntroResponse intro = tourApiClient.getIntro(
                candidate.externalContentId(),
                candidate.contentTypeId()
        );
        TourApiPlaceImageResponse images = tourApiClient.getImages(candidate.externalContentId());
        return new PlaceEnrichmentItem(
                detail.overview(),
                detail.homepage(),
                detail.rawJson(),
                intro.openingHoursText(),
                intro.closedDaysText(),
                intro.useFeeText(),
                intro.parkingText(),
                intro.requiresManualCheck(),
                intro.rawJson(),
                images.items().stream()
                        .map(image -> new PlaceEnrichmentItem.Image(
                                image.url(),
                                image.thumbnailUrl(),
                                image.copyrightType()
                        ))
                        .toList()
        );
    }

    private Optional<PlaceDiscoveryItem> toDiscoveryItem(
            String requestedContentTypeId,
            TourApiPlaceListResponse.Item item
    ) {
        String contentId = blankToNull(item.contentId());
        String name = blankToNull(item.title());
        BigDecimal longitude = decimalOrNull(item.longitude());
        BigDecimal latitude = decimalOrNull(item.latitude());
        if (contentId == null || name == null || !validCoordinates(longitude, latitude)) {
            log.warn("Skipped TourAPI place because required fields are invalid. contentId={}", contentId);
            return Optional.empty();
        }

        String contentTypeId = Optional.ofNullable(blankToNull(item.contentTypeId()))
                .orElse(requestedContentTypeId);
        return Optional.of(new PlaceDiscoveryItem(
                contentId,
                contentTypeId,
                name,
                blankToNull(item.category()),
                blankToNull(item.address()),
                longitude,
                latitude,
                blankToNull(item.firstImage()),
                sourceModifiedAt(item.modifiedTime())
        ));
    }

    private boolean validCoordinates(BigDecimal longitude, BigDecimal latitude) {
        return longitude != null
                && latitude != null
                && longitude.compareTo(BigDecimal.valueOf(-180)) >= 0
                && longitude.compareTo(BigDecimal.valueOf(180)) <= 0
                && latitude.compareTo(BigDecimal.valueOf(-90)) >= 0
                && latitude.compareTo(BigDecimal.valueOf(90)) <= 0;
    }

    private LocalDateTime sourceModifiedAt(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(normalized, SOURCE_TIME_FORMAT);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private BigDecimal decimalOrNull(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return new BigDecimal(normalized).setScale(8, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode().name();
        }
        return "INGESTION_ERROR";
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static class Counters {
        private int fetched;
        private int discovered;
        private int enriched;
        private int unchanged;
        private int pending;
        private int failed;
        private int skipped;
        private int apiRequests;

        private TourApiPlaceIngestionResult toResult() {
            return new TourApiPlaceIngestionResult(
                    fetched,
                    discovered,
                    enriched,
                    unchanged,
                    pending,
                    failed,
                    skipped,
                    this.apiRequests,
                    false
            );
        }
    }
}
