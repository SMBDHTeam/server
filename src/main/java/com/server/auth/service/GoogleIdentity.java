package com.server.auth.service;

/**
 * 검증을 통과한 구글 ID 토큰에서 뽑은 사용자 정보.
 *
 * @param subject 구글 고유 ID({@code sub}). 이메일과 달리 바뀌지 않으므로 식별자로 쓴다
 */
public record GoogleIdentity(String subject, String email, String name, String pictureUrl) {
}
