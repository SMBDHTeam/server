package com.server.place.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "place_operating_infos")
public class PlaceOperatingInfo {

    @Id
    @Column(name = "place_id")
    private Long placeId;

    @MapsId
    @OneToOne
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(name = "opening_hours_text", columnDefinition = "text")
    private String openingHoursText;

    @Column(name = "closed_days_text", columnDefinition = "text")
    private String closedDaysText;

    @Column(name = "use_fee_text", columnDefinition = "text")
    private String useFeeText;

    @Column(name = "parking_text", columnDefinition = "text")
    private String parkingText;

    @Column(name = "requires_manual_check", nullable = false)
    private boolean requiresManualCheck;

    @Column(name = "raw_json", columnDefinition = "text")
    private String rawJson;

    protected PlaceOperatingInfo() {
    }

    public PlaceOperatingInfo(
            Place place,
            String openingHoursText,
            String closedDaysText,
            String useFeeText,
            String parkingText,
            boolean requiresManualCheck,
            String rawJson
    ) {
        this.place = place;
        this.openingHoursText = openingHoursText;
        this.closedDaysText = closedDaysText;
        this.useFeeText = useFeeText;
        this.parkingText = parkingText;
        this.requiresManualCheck = requiresManualCheck;
        this.rawJson = rawJson;
        place.setOperatingInfo(this);
    }

    public String getOpeningHoursText() {
        return openingHoursText;
    }

    public String getClosedDaysText() {
        return closedDaysText;
    }

    public String getUseFeeText() {
        return useFeeText;
    }

    public String getParkingText() {
        return parkingText;
    }

    public boolean isRequiresManualCheck() {
        return requiresManualCheck;
    }

    public void update(
            String openingHoursText,
            String closedDaysText,
            String useFeeText,
            String parkingText,
            boolean requiresManualCheck,
            String rawJson
    ) {
        this.openingHoursText = openingHoursText;
        this.closedDaysText = closedDaysText;
        this.useFeeText = useFeeText;
        this.parkingText = parkingText;
        this.requiresManualCheck = requiresManualCheck;
        this.rawJson = rawJson;
    }
}
