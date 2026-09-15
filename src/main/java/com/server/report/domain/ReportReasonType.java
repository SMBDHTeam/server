package com.server.report.domain;

/**
 * 신고 사유 유형.
 *
 * <p>자유 입력만 받으면 관리자가 글을 전부 읽어야 무엇이 많은지 알 수 있고, 같은 뜻을
 * 사람마다 다르게 적어 모아 볼 수 없다. 유형을 먼저 고르게 하고 설명은 덧붙이게 한다.
 *
 * <p>화면 문구는 클라이언트가 들고 있다. 여기 이름은 Swagger 설명용이다.
 */
public enum ReportReasonType {
    /** 스팸·광고. */
    SPAM,
    /** 욕설·비하·괴롭힘. */
    ABUSE,
    /** 음란하거나 선정적인 내용. */
    SEXUAL,
    /** 불법이거나 위험한 내용. */
    ILLEGAL,
    /** 개인정보 노출. */
    PRIVACY,
    /** 잘못된 장소·여행 정보. */
    FALSE_INFO,
    /** 기타. 유형으로 설명할 수 없으므로 설명이 필요하다. */
    OTHER;

    public boolean requiresDetail() {
        return this == OTHER;
    }
}
