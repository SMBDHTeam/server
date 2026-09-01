package com.server.post.dto;

import com.server.post.domain.PostPlaceTag;

/**
 * 응답에 필요한 장소 태그 값만 담는 조회 전용 모델.
 *
 * <p>{@code Place} 엔티티를 그대로 읽으면 {@code mappedBy} 로 연결된 상세·운영정보가
 * 장소마다 추가 질의를 발생시킨다. 커뮤니티는 장소명과 ID만 쓰므로 필요한 열만 가져온다.
 */
public record PostPlaceTagView(
        Long postId,
        Long mediaId,
        Long placeId,
        String placeName
) {

    /** 방금 저장해 이미 메모리에 있는 엔티티를 변환할 때 쓴다. */
    public static PostPlaceTagView from(PostPlaceTag tag) {
        return new PostPlaceTagView(
                tag.getPost().getId(),
                tag.getMedia() == null ? null : tag.getMedia().getId(),
                tag.getPlace().getId(),
                tag.getPlace().getName());
    }
}
