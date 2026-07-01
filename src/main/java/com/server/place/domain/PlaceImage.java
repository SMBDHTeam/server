package com.server.place.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "place_images")
public class PlaceImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "thumbnail_url", columnDefinition = "text")
    private String thumbnailUrl;

    @Column(name = "copyright_type")
    private String copyrightType;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected PlaceImage() {
    }

    public PlaceImage(
            Place place,
            String url,
            String thumbnailUrl,
            String copyrightType,
            int displayOrder
    ) {
        this.place = place;
        this.url = url;
        this.thumbnailUrl = thumbnailUrl;
        this.copyrightType = copyrightType;
        this.displayOrder = displayOrder;
        place.addImage(this);
    }

    public String getUrl() {
        return url;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getCopyrightType() {
        return copyrightType;
    }
}
