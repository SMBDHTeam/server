package com.server.place.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "place_details")
public class PlaceDetail {

    @Id
    @Column(name = "place_id")
    private Long placeId;

    @MapsId
    @OneToOne
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(columnDefinition = "text")
    private String overview;

    @Column(columnDefinition = "text")
    private String homepage;

    @Column(name = "raw_json", columnDefinition = "text")
    private String rawJson;

    protected PlaceDetail() {
    }

    public PlaceDetail(Place place, String overview, String homepage, String rawJson) {
        this.place = place;
        this.overview = overview;
        this.homepage = homepage;
        this.rawJson = rawJson;
        place.setDetail(this);
    }

    public String getOverview() {
        return overview;
    }
}
