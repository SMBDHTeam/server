package com.server.place.domain;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
import java.util.Objects;

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

    @Column(name = "place_url", columnDefinition = "text")
    private String placeUrl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "source_modified_at")
    private LocalDateTime sourceModifiedAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "ingestion_status", nullable = false)
    private PlaceIngestionStatus ingestionStatus;

    @Column(name = "ingestion_retry_count", nullable = false)
    private int ingestionRetryCount;

    @Column(name = "ingestion_last_error", columnDefinition = "text")
    private String ingestionLastError;

    @Column(name = "ingestion_next_retry_at")
    private LocalDateTime ingestionNextRetryAt;

    @OneToOne(mappedBy = "place", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private PlaceDetail detail;

    @OneToOne(mappedBy = "place", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private PlaceOperatingInfo operatingInfo;

    @OrderBy("displayOrder ASC")
    @OneToMany(mappedBy = "place", cascade = CascadeType.ALL, orphanRemoval = true)
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
        this.lastSeenAt = this.createdAt;
        this.lastSyncedAt = this.createdAt;
        this.ingestionStatus = PlaceIngestionStatus.SYNCED;
    }

    public Long getId() {
        return id;
    }

    public String getExternalContentId() {
        return externalContentId;
    }

    public String getSource() {
        return source;
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

    public String getPlaceUrl() {
        return placeUrl;
    }

    public void updateResolvedPlace(
            String name,
            String category,
            String address,
            BigDecimal longitude,
            BigDecimal latitude,
            String placeUrl
    ) {
        this.name = name;
        this.category = category;
        this.address = address;
        this.longitude = longitude;
        this.latitude = latitude;
        this.placeUrl = placeUrl;
        this.lastSeenAt = LocalDateTime.now();
        this.lastSyncedAt = this.lastSeenAt;
        this.updatedAt = this.lastSeenAt;
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

    public LocalDateTime getSourceModifiedAt() {
        return sourceModifiedAt;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public LocalDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }

    public PlaceIngestionStatus getIngestionStatus() {
        return ingestionStatus;
    }

    public int getIngestionRetryCount() {
        return ingestionRetryCount;
    }

    public String getIngestionLastError() {
        return ingestionLastError;
    }

    public LocalDateTime getIngestionNextRetryAt() {
        return ingestionNextRetryAt;
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

    public void updateBasic(
            String contentTypeId,
            String name,
            String category,
            String address,
            BigDecimal longitude,
            BigDecimal latitude,
            String primaryImageUrl
    ) {
        this.contentTypeId = contentTypeId;
        this.name = name;
        this.category = category;
        this.address = address;
        this.longitude = longitude;
        this.latitude = latitude;
        this.primaryImageUrl = primaryImageUrl;
        this.updatedAt = LocalDateTime.now();
    }

    public void replaceImages(List<PlaceImage> images) {
        this.images.clear();
        this.images.addAll(images);
        this.updatedAt = LocalDateTime.now();
    }

    public void markNewDiscovery(LocalDateTime sourceModifiedAt, LocalDateTime seenAt) {
        this.sourceModifiedAt = sourceModifiedAt;
        this.lastSeenAt = seenAt;
        this.ingestionStatus = PlaceIngestionStatus.PENDING;
        this.lastSyncedAt = null;
        this.ingestionLastError = null;
        this.ingestionNextRetryAt = null;
    }

    public boolean recordDiscovery(
            String contentTypeId,
            String name,
            String category,
            String address,
            BigDecimal longitude,
            BigDecimal latitude,
            String primaryImageUrl,
            LocalDateTime incomingSourceModifiedAt,
            LocalDateTime seenAt
    ) {
        boolean basicChanged = !sameBasic(
                contentTypeId,
                name,
                category,
                address,
                longitude,
                latitude,
                primaryImageUrl
        );
        boolean sourceChanged = incomingSourceModifiedAt != null
                && !Objects.equals(sourceModifiedAt, incomingSourceModifiedAt);

        if (basicChanged) {
            updateBasic(contentTypeId, name, category, address, longitude, latitude, primaryImageUrl);
        }
        if (incomingSourceModifiedAt != null) {
            this.sourceModifiedAt = incomingSourceModifiedAt;
        }
        this.lastSeenAt = seenAt;

        if (basicChanged || sourceChanged) {
            this.ingestionStatus = PlaceIngestionStatus.PENDING;
            this.ingestionLastError = null;
            this.ingestionRetryCount = 0;
            this.ingestionNextRetryAt = null;
        }
        return this.ingestionStatus != PlaceIngestionStatus.SYNCED
                && (this.ingestionNextRetryAt == null || !seenAt.isBefore(this.ingestionNextRetryAt));
    }

    public void markIngestionSynced(LocalDateTime syncedAt) {
        this.ingestionStatus = PlaceIngestionStatus.SYNCED;
        this.lastSyncedAt = syncedAt;
        this.ingestionRetryCount = 0;
        this.ingestionLastError = null;
        this.ingestionNextRetryAt = null;
        this.updatedAt = syncedAt;
    }

    public void markIngestionFailed(String errorCode, LocalDateTime failedAt) {
        this.ingestionStatus = PlaceIngestionStatus.FAILED;
        this.ingestionRetryCount++;
        this.ingestionLastError = errorCode;
        this.ingestionNextRetryAt = failedAt.plusDays(retryDelayDays(ingestionRetryCount));
        this.updatedAt = failedAt;
    }

    private int retryDelayDays(int retryCount) {
        return Math.min(7, 1 << Math.min(3, Math.max(0, retryCount - 1)));
    }

    private boolean sameBasic(
            String contentTypeId,
            String name,
            String category,
            String address,
            BigDecimal longitude,
            BigDecimal latitude,
            String primaryImageUrl
    ) {
        return Objects.equals(this.contentTypeId, contentTypeId)
                && Objects.equals(this.name, name)
                && Objects.equals(this.category, category)
                && Objects.equals(this.address, address)
                && sameDecimal(this.longitude, longitude)
                && sameDecimal(this.latitude, latitude)
                && Objects.equals(this.primaryImageUrl, primaryImageUrl);
    }

    private boolean sameDecimal(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }
}
