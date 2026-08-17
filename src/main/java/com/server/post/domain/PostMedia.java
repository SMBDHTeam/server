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

    @Column(name = "media_type", nullable = false)
    private String mediaType;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected PostMedia() {
    }

    public PostMedia(Post post, String mediaType, String url, int sortOrder) {
        this.post = post;
        this.mediaType = mediaType;
        this.url = url;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public Post getPost() {
        return post;
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getUrl() {
        return url;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}

