package com.server.post.domain;

import com.server.place.domain.Place;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

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
     * 이 장소를 붙인 사진. 사진마다 다른 곳을 다녀왔을 수 있어 게시물이 아니라 사진에
     * 붙인다. 장소를 붙이지 않은 사진이 있을 수 있고 예전 데이터는 어느 사진의 것인지
     * 되짚을 수 없어 {@code null} 을 허용한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id")
    private PostMedia media;

    protected PostPlaceTag() {
    }

    public PostPlaceTag(Post post, PostMedia media, Place place) {
        this.post = post;
        this.media = media;
        this.place = place;
    }

    public Long getId() {
        return id;
    }

    public Post getPost() {
        return post;
    }

    public PostMedia getMedia() {
        return media;
    }

    public Place getPlace() {
        return place;
    }
}
