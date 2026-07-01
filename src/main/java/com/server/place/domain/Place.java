package com.server.place.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "places")
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

    public String getName() {
        return name;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }
}
