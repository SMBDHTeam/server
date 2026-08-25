package com.server.hashtag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "hashtags",
        uniqueConstraints = @UniqueConstraint(name = "uk_hashtags_name", columnNames = {"name"})
)
public class Hashtag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소문자로 정규화해 저장한다. #Busan 과 #busan 은 같은 태그다. */
    @Column(nullable = false)
    private String name;

    /** 자동완성 정렬 기준. 게시물이 추가·삭제될 때 함께 증감시킨다. */
    @Column(name = "post_count", nullable = false)
    private int postCount = 0;

    protected Hashtag() {
    }

    public Hashtag(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getPostCount() {
        return postCount;
    }
}
