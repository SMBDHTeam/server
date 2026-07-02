package com.server.place.ingestion;

import com.server.place.domain.Place;
import com.server.place.domain.PlaceDetail;
import com.server.place.domain.PlaceImage;
import com.server.place.domain.PlaceOperatingInfo;
import com.server.place.repository.PlaceRepository;
import java.util.List;
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
    public void upsert(PlaceIngestionItem item) {
        Place place = placeRepository.findBySourceAndExternalContentId(SOURCE, item.externalContentId())
                .map(existing -> updatePlace(existing, item))
                .orElseGet(() -> createPlace(item));

        upsertDetail(place, item);
        upsertOperatingInfo(place, item);
        replaceImages(place, item.images());
        placeRepository.save(place);
    }

    private Place createPlace(PlaceIngestionItem item) {
        return new Place(
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
    }

    private Place updatePlace(Place place, PlaceIngestionItem item) {
        place.updateBasic(
                item.contentTypeId(),
                item.name(),
                item.category(),
                item.address(),
                item.longitude(),
                item.latitude(),
                item.primaryImageUrl()
        );
        return place;
    }

    private void upsertDetail(Place place, PlaceIngestionItem item) {
        if (place.getDetail() == null) {
            new PlaceDetail(place, item.overview(), item.homepage(), item.detailRawJson());
            return;
        }
        place.getDetail().update(item.overview(), item.homepage(), item.detailRawJson());
    }

    private void upsertOperatingInfo(Place place, PlaceIngestionItem item) {
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

    private void replaceImages(Place place, List<PlaceIngestionItem.Image> images) {
        place.replaceImages(IntStream.range(0, images.size())
                .mapToObj(index -> {
                    PlaceIngestionItem.Image image = images.get(index);
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
}
