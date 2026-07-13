package com.server.place.ingestion;

import com.server.place.domain.Place;
import com.server.place.domain.PlaceDetail;
import com.server.place.domain.PlaceImage;
import com.server.place.domain.PlaceOperatingInfo;
import com.server.place.repository.PlaceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TourApiPlaceWriter {

    private static final String SOURCE = "TOUR_API";

    private final PlaceRepository placeRepository;

    public TourApiPlaceWriter(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    @Transactional
    public PlaceSyncCandidate discover(PlaceDiscoveryItem item, LocalDateTime seenAt) {
        return placeRepository.findBySourceAndExternalContentId(SOURCE, item.externalContentId())
                .map(existing -> updateDiscovery(existing, item, seenAt))
                .orElseGet(() -> createDiscovery(item, seenAt));
    }

    @Transactional
    public void completeEnrichment(String externalContentId, PlaceEnrichmentItem item, LocalDateTime syncedAt) {
        Place place = requiredPlace(externalContentId);
        if (item.hasDetail()) {
            upsertDetail(place, item);
        }
        if (item.hasOperatingInfo()) {
            upsertOperatingInfo(place, item);
        }
        if (!sameImages(place.getImages(), item.images())) {
            replaceImages(place, item.images());
        }
        place.markIngestionSynced(syncedAt);
        placeRepository.save(place);
    }

    @Transactional
    public void markFailed(String externalContentId, String errorCode, LocalDateTime failedAt) {
        Place place = requiredPlace(externalContentId);
        place.markIngestionFailed(errorCode, failedAt);
        placeRepository.save(place);
    }

    private PlaceSyncCandidate createDiscovery(PlaceDiscoveryItem item, LocalDateTime seenAt) {
        Place place = new Place(
                SOURCE,
                item.externalContentId(),
                item.contentTypeId(),
                item.name(),
                item.category(),
                item.address(),
                item.longitude(),
                item.latitude(),
                item.primaryImageUrl()
        );
        place.markNewDiscovery(item.sourceModifiedAt(), seenAt);
        placeRepository.save(place);
        return new PlaceSyncCandidate(item.externalContentId(), item.contentTypeId(), true, true);
    }

    private PlaceSyncCandidate updateDiscovery(Place place, PlaceDiscoveryItem item, LocalDateTime seenAt) {
        boolean enrichmentRequired = place.recordDiscovery(
                item.contentTypeId(),
                item.name(),
                item.category(),
                item.address(),
                item.longitude(),
                item.latitude(),
                item.primaryImageUrl(),
                item.sourceModifiedAt(),
                seenAt
        );
        placeRepository.save(place);
        return new PlaceSyncCandidate(item.externalContentId(), item.contentTypeId(), enrichmentRequired, false);
    }

    private Place requiredPlace(String externalContentId) {
        return placeRepository.findBySourceAndExternalContentId(SOURCE, externalContentId)
                .orElseThrow(() -> new IllegalStateException("Discovered place is missing"));
    }

    private void upsertDetail(Place place, PlaceEnrichmentItem item) {
        if (place.getDetail() == null) {
            new PlaceDetail(place, item.overview(), item.homepage(), item.detailRawJson());
            return;
        }
        place.getDetail().update(item.overview(), item.homepage(), item.detailRawJson());
    }

    private void upsertOperatingInfo(Place place, PlaceEnrichmentItem item) {
        if (place.getOperatingInfo() == null) {
            new PlaceOperatingInfo(
                    place,
                    item.openingHoursText(),
                    item.closedDaysText(),
                    item.useFeeText(),
                    item.parkingText(),
                    item.requiresManualCheck(),
                    item.operatingRawJson()
            );
            return;
        }
        place.getOperatingInfo().update(
                item.openingHoursText(),
                item.closedDaysText(),
                item.useFeeText(),
                item.parkingText(),
                item.requiresManualCheck(),
                item.operatingRawJson()
        );
    }

    private boolean sameImages(List<PlaceImage> current, List<PlaceEnrichmentItem.Image> incoming) {
        if (current.size() != incoming.size()) {
            return false;
        }
        for (int index = 0; index < current.size(); index++) {
            PlaceImage currentImage = current.get(index);
            PlaceEnrichmentItem.Image incomingImage = incoming.get(index);
            if (!Objects.equals(currentImage.getUrl(), incomingImage.url())
                    || !Objects.equals(currentImage.getThumbnailUrl(), incomingImage.thumbnailUrl())
                    || !Objects.equals(currentImage.getCopyrightType(), incomingImage.copyrightType())) {
                return false;
            }
        }
        return true;
    }

    private void replaceImages(Place place, List<PlaceEnrichmentItem.Image> images) {
        place.replaceImages(IntStream.range(0, images.size())
                .mapToObj(index -> {
                    PlaceEnrichmentItem.Image image = images.get(index);
                    return PlaceImage.of(
                            place,
                            image.url(),
                            image.thumbnailUrl(),
                            image.copyrightType(),
                            index + 1
                    );
                })
                .toList());
    }

    public record PlaceSyncCandidate(
            String externalContentId,
            String contentTypeId,
            boolean enrichmentRequired,
            boolean created
    ) {
    }
}
