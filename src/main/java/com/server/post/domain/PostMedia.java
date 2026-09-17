package com.server.post.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "post_media")
public class PostMedia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "post_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Post post;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private MediaType mediaType;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    /** 목록용 축소본. 만들지 못한 형식과 이 열이 생기기 전의 사진은 비어 있다. */
    @Column(name = "thumbnail_url", columnDefinition = "text")
    private String thumbnailUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected PostMedia() {
    }

    public PostMedia(Post post, MediaType mediaType, String url, int sortOrder) {
        this(post, mediaType, url, null, sortOrder);
    }

    public PostMedia(Post post, MediaType mediaType, String url, String thumbnailUrl, int sortOrder) {
        this.post = post;
        this.mediaType = mediaType;
        this.url = url;
        this.thumbnailUrl = thumbnailUrl;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public Post getPost() {
        return post;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public String getUrl() {
        return url;
    }

    /** @return 축소본 주소, 없으면 {@code null}. 화면에 그릴 때는 {@link #getListImageUrl()} 을 쓴다 */
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    /** 목록에 그릴 주소. 축소본이 없으면 원본으로 돌아간다. */
    public String getListImageUrl() {
        return thumbnailUrl == null || thumbnailUrl.isBlank() ? url : thumbnailUrl;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}

