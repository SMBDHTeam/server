package com.server.post.domain;

import com.server.place.domain.Place;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "post_place_tags")
public class PostPlaceTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    /**
     * 사진이 실제로 촬영된 지점. 장소 대표 좌표는 {@link Place}에 있으므로 복사하지 않고,
     * EXIF GPS가 없어 촬영 지점을 알 수 없으면 {@code null}로 둔다.
     */
    @Column(precision = 12, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 12, scale = 8)
    private BigDecimal longitude;

    protected PostPlaceTag() {
    }

    public PostPlaceTag(Post post, Place place, BigDecimal latitude, BigDecimal longitude) {
        this.post = post;
        this.place = place;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getId() {
        return id;
    }

    public Post getPost() {
        return post;
    }

    public Place getPlace() {
        return place;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }
}
