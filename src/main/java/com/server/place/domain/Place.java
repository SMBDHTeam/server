package com.server.place.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "places",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_places_source_external_content_id",
                columnNames = {"source", "external_content_id"}
        )
)
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String source;

    @Column(name = "external_content_id", nullable = false)
    private String externalContentId;

    @Column(name = "content_type_id")
    private String contentTypeId;

    @Column(nullable = false)
    private String name;

    private String category;

    private String address;

    @Column(nullable = false, precision = 12, scale = 8)
    private BigDecimal longitude;

    @Column(nullable = false, precision = 12, scale = 8)
    private BigDecimal latitude;

    @Column(name = "primary_image_url", columnDefinition = "text")
    private String primaryImageUrl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToOne(mappedBy = "place")
    private PlaceDetail detail;

    @OneToOne(mappedBy = "place")
    private PlaceOperatingInfo operatingInfo;

    @OrderBy("displayOrder ASC")
    @OneToMany(mappedBy = "place")
    private List<PlaceImage> images = new ArrayList<>();

    protected Place() {
    }

    public Place(
            String source,
            String externalContentId,
            String contentTypeId,
            String name,
            String category,
            String address,
            BigDecimal longitude,
            BigDecimal latitude,
            String primaryImageUrl
    ) {
        this.source = source;
        this.externalContentId = externalContentId;
        this.contentTypeId = contentTypeId;
        this.name = name;
        this.category = category;
        this.address = address;
        this.longitude = longitude;
        this.latitude = latitude;
        this.primaryImageUrl = primaryImageUrl;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getExternalContentId() {
        return externalContentId;
    }

    public String getContentTypeId() {
        return contentTypeId;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getAddress() {
        return address;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public String getPrimaryImageUrl() {
        return primaryImageUrl;
    }

    public PlaceDetail getDetail() {
        return detail;
    }

    public PlaceOperatingInfo getOperatingInfo() {
        return operatingInfo;
    }

    public List<PlaceImage> getImages() {
        return images;
    }

    public void setDetail(PlaceDetail detail) {
        this.detail = detail;
    }

    public void setOperatingInfo(PlaceOperatingInfo operatingInfo) {
        this.operatingInfo = operatingInfo;
    }

    public void addImage(PlaceImage image) {
        this.images.add(image);
    }
}
