package com.server.post.domain;

import com.server.user.domain.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "posts")
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "user_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    @Column(name = "comment_count", nullable = false)
    private int commentCount = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 관리자가 지웠는지.
     *
     * <p>본인 삭제와 관리자 삭제가 같은 컬럼을 쓰면 구분이 불가능하다. 그러면 작성자가
     * 제재로 지워진 글을 복구 기능으로 그대로 되살릴 수 있다.
     */
    @Column(name = "deleted_by_admin", nullable = false)
    private boolean deletedByAdmin;

    protected Post() {
    }

    public Post(User user, String content) {
        this.user = user;
        this.content = content;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }


    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getContent() {
        return content;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public boolean isWrittenBy(Long userId) {
        return user.getId().equals(userId);
    }

    public void updateContent(String content) {
        this.content = content;
        this.updatedAt = LocalDateTime.now();
    }

    /** 물리 삭제하지 않는다. 기획상 삭제 후 30일간 복구할 수 있어야 한다. */
    public void delete() {
        this.deletedAt = LocalDateTime.now();
        this.updatedAt = this.deletedAt;
        this.deletedByAdmin = false;
    }

    /**
     * 관리자 조치로 삭제한다.
     *
     * <p>지우지 않고 표시만 남기는 것은 본인 삭제와 같다. 오판을 되돌릴 수 있어야 하고,
     * 분쟁이 붙었을 때 원문이 근거가 되기 때문이다. 다만 작성자는 되살릴 수 없다.
     */
    public void deleteByAdmin() {
        this.deletedAt = LocalDateTime.now();
        this.updatedAt = this.deletedAt;
        this.deletedByAdmin = true;
    }

    /**
     * 삭제 표시를 지운다. 해시태그 연결은 삭제할 때 실제로 지웠으므로 본문에서 다시
     * 뽑아 연결해야 한다. {@code PostService.restore} 가 함께 처리한다.
     */
    public void restore() {
        this.deletedAt = null;
        this.updatedAt = LocalDateTime.now();
        this.deletedByAdmin = false;
    }

    /** 관리자 조치로 지워진 글인지. 작성자 복구를 막는 판단에 쓴다. */
    public boolean isDeletedByAdmin() {
        return deletedByAdmin;
    }

    /** 삭제한 지 {@code days} 일이 지났는지. 지난 글은 복구할 수 없다. */
    public boolean isRestorableWithin(int days) {
        return deletedAt != null && deletedAt.isAfter(LocalDateTime.now().minusDays(days));
    }
}
