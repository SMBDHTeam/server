package com.server.place.ingestion;

import com.server.external.tourapi.TourApiClient;
import com.server.external.tourapi.TourApiPlaceDetailResponse;
import com.server.external.tourapi.TourApiPlaceImageResponse;
import com.server.external.tourapi.TourApiPlaceIntroResponse;
import com.server.external.tourapi.TourApiPlaceListResponse;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TourApiPlaceIngestionService {

    private static final Logger log = LoggerFactory.getLogger(TourApiPlaceIngestionService.class);

    private final TourApiClient tourApiClient;
    private final TourApiPlaceWriter placeWriter;
    private final TourApiPlaceIngestionProperties properties;

    public TourApiPlaceIngestionService(
            TourApiClient tourApiClient,
            TourApiPlaceWriter placeWriter,
            TourApiPlaceIngestionProperties properties
    ) {
        this.tourApiClient = tourApiClient;
        this.placeWriter = placeWriter;
        this.properties = properties;
    }

    public TourApiPlaceIngestionResult ingestConfigured() {
        TourApiPlaceIngestionResult result = TourApiPlaceIngestionResult.empty();
        for (String contentTypeId : properties.contentTypeIds()) {
            result = result.plus(ingestContentType(contentTypeId));
        }
        return result;
    }

    private TourApiPlaceIngestionResult ingestContentType(String contentTypeId) {
        TourApiPlaceIngestionResult result = TourApiPlaceIngestionResult.empty();
        int pageNo = 1;
        while (pageNo <= properties.maxPages()) {
            TourApiPlaceListResponse page = tourApiClient.searchPlaces(
                    properties.areaCode(),
                    contentTypeId,
                    pageNo,
                    properties.pageSize()
            );
            if (page.items().isEmpty()) {
                break;
            }
            result = result.plus(ingestPage(contentTypeId, page));
            if (pageNo * properties.pageSize() >= page.totalCount()) {
                break;
            }
            pageNo++;
        }
        return result;
    }

    private TourApiPlaceIngestionResult ingestPage(String requestedContentTypeId, TourApiPlaceListResponse page) {
        int saved = 0;
        int skipped = 0;
        for (TourApiPlaceListResponse.Item item : page.items()) {
            Optional<PlaceIngestionItem> ingestionItem = toIngestionItem(requestedContentTypeId, item);
            if (ingestionItem.isEmpty()) {
                skipped++;
                continue;
            }
            placeWriter.upsert(ingestionItem.get());
            saved++;
        }
        return new TourApiPlaceIngestionResult(page.items().size(), saved, skipped);
    }

    private Optional<PlaceIngestionItem> toIngestionItem(
            String requestedContentTypeId,
            TourApiPlaceListResponse.Item item
    ) {
        String contentId = blankToNull(item.contentId());
        String name = blankToNull(item.title());
        BigDecimal longitude = decimalOrNull(item.longitude());
        BigDecimal latitude = decimalOrNull(item.latitude());
        if (contentId == null || name == null || longitude == null || latitude == null) {
            log.warn("Skipped TourAPI place because required fields are missing. contentId={}", contentId);
            return Optional.empty();
        }

        String contentTypeId = Optional.ofNullable(blankToNull(item.contentTypeId()))
                .orElse(requestedContentTypeId);
        TourApiPlaceDetailResponse detail = tourApiClient.getCommonDetail(contentId, contentTypeId);
        TourApiPlaceIntroResponse intro = tourApiClient.getIntro(contentId, contentTypeId);
        TourApiPlaceImageResponse images = tourApiClient.getImages(contentId);

        return Optional.of(new PlaceIngestionItem(
                contentId,
                contentTypeId,
                name,
                blankToNull(item.category()),
                blankToNull(item.address()),
                longitude,
                latitude,
                blankToNull(item.firstImage()),
                detail.overview(),
                detail.homepage(),
                detail.rawJson(),
                intro.openingHoursText(),
                intro.closedDaysText(),
                intro.useFeeText(),
                intro.parkingText(),
                intro.requiresManualCheck(),
                intro.rawJson(),
                images.items()
                        .stream()
                        .map(image -> new PlaceIngestionItem.Image(
                                image.url(),
                                image.thumbnailUrl(),
                                image.copyrightType()
                        ))
                        .toList()
        ));
    }

    private BigDecimal decimalOrNull(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
