package com.server.post.domain;

/**
 * 신고 처리 상태.
 *
 * <p>{@code REVIEWING} 을 둔 이유는 관리자가 여럿일 때 같은 신고를 두 사람이 동시에
 * 들여다보는 것을 줄이기 위함이다. 확인만 하고 조치하지 않은 것과 아직 아무도 보지 않은
 * 것을 구분하지 못하면 대기 목록이 계속 같은 항목으로 채워진다.
 */
public enum ReportStatus {
    /** 접수만 된 상태. */
    PENDING,
    /** 관리자가 확인 중. */
    REVIEWING,
    /** 조치를 마침. */
    RESOLVED,
    /** 신고 사유가 되지 않는다고 판단함. */
    REJECTED;

    /** 처리가 끝난 상태인지. */
    public boolean isClosed() {
        return this == RESOLVED || this == REJECTED;
    }
}
