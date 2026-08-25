package com.server.common.error;

import org.springframework.http.HttpStatusCode;

public enum ErrorCode {

    INVALID_SCHEDULE_CONDITION(400, "일정 조건이 올바르지 않습니다."),
    INVALID_SCHEDULE_PREVIEW_REQUEST(400, "일정 미리보기 요청이 올바르지 않습니다."),
    FIXED_BASE_LOCATION_REQUIRED(400, "고정 숙소 위치가 필요합니다."),
    PER_NIGHT_LOCATION_MISSING(400, "숙박일별 숙소 위치가 필요합니다."),
    MUST_VISIT_PLACE_LIMIT_EXCEEDED(400, "필수 방문 장소 수가 허용 범위를 초과했습니다."),
    INVALID_EXTERNAL_PLACE(400, "외부 장소 정보가 올바르지 않습니다."),
    INVALID_PLACE_SEARCH_REQUEST(400, "장소 검색 조건이 올바르지 않습니다."),
    MALFORMED_REQUEST(400, "요청 형식이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(404, "요청한 경로를 찾을 수 없습니다."),
    IDEMPOTENCY_KEY_REQUIRED(400, "Idempotency-Key 헤더가 필요합니다."),
    IDEMPOTENCY_KEY_REUSED(409, "같은 멱등성 키를 다른 요청에 사용할 수 없습니다."),
    PREVIEW_ALREADY_CONSUMED(409, "이미 일정 생성에 사용된 미리보기입니다."),
    SCHEDULE_CREATION_IN_PROGRESS(409, "같은 요청의 일정 생성이 진행 중입니다."),
    PREVIEW_EXPIRED(410, "일정 미리보기가 만료되었습니다."),
    SCHEDULE_PREVIEW_NOT_FOUND(404, "일정 미리보기를 찾을 수 없습니다."),
    FIXED_EVENT_UNREACHABLE(422, "고정 행사 시간에 도착할 수 없습니다."),
    END_CONSTRAINT_UNREACHABLE(422, "마지막 도착 제약을 만족할 수 없습니다."),
    SCHEDULE_NOT_FOUND(404, "일정을 찾을 수 없습니다."),
    PLACE_NOT_FOUND(404, "장소를 찾을 수 없습니다."),
    SHARE_LINK_NOT_FOUND(404, "공유 링크를 찾을 수 없습니다."),
    POST_NOT_FOUND(404, "게시물을 찾을 수 없습니다."),
    COMMENT_NOT_FOUND(404, "댓글을 찾을 수 없습니다."),
    USER_NOT_FOUND(404, "사용자를 찾을 수 없습니다."),
    INVALID_POST_REQUEST(400, "게시물 요청이 올바르지 않습니다."),
    INVALID_USER_REQUEST(400, "사용자 요청이 올바르지 않습니다."),
    INVALID_COMMENT_REQUEST(400, "댓글 요청이 올바르지 않습니다."),
    INVALID_FOLLOW_REQUEST(400, "팔로우 요청이 올바르지 않습니다."),
    INVALID_FEED_REQUEST(400, "피드 조회 조건이 올바르지 않습니다."),
    POST_ACCESS_DENIED(403, "본인의 게시물만 수정·삭제·복구할 수 있습니다."),
    POST_RESTORE_WINDOW_EXPIRED(410, "복구할 수 있는 기간이 지난 게시물입니다."),
    COMMENT_ACCESS_DENIED(403, "본인의 댓글만 삭제할 수 있습니다."),
    NICKNAME_ALREADY_USED(409, "이미 사용 중인 닉네임입니다."),
    ALREADY_REPORTED(409, "이미 신고한 대상입니다."),
    INVALID_BLOCK_REQUEST(400, "차단 요청이 올바르지 않습니다."),
    FOLLOW_BLOCKED_USER(400, "차단한 사용자는 팔로우할 수 없습니다. 차단을 먼저 해제해 주세요."),
    COMMENT_NOT_ALLOWED(403, "이 게시물에는 댓글을 쓸 수 없습니다."),
    TRANSIT_ROUTE_NOT_FOUND(422, "장소 사이 대중교통 경로를 찾지 못했습니다."),
    FACILITY_TYPE_NOT_SUPPORTED(501, "지원하지 않는 편의시설 유형입니다."),
    EXTERNAL_PROVIDER_UNAVAILABLE(503, "외부 서비스가 응답하지 않습니다."),
    INTERNAL_ERROR(500, "서버에서 요청을 처리하지 못했습니다.");

    private final HttpStatusCode status;
    private final String message;

    ErrorCode(int status, String message) {
        this.status = HttpStatusCode.valueOf(status);
        this.message = message;
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
