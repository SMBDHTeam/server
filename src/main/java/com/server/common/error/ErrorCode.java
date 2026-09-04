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
    NOTIFICATION_NOT_FOUND(404, "알림을 찾을 수 없습니다."),
    INVALID_POST_REQUEST(400, "게시물 요청이 올바르지 않습니다."),
    INVALID_USER_REQUEST(400, "사용자 요청이 올바르지 않습니다."),
    INVALID_COMMENT_REQUEST(400, "댓글 요청이 올바르지 않습니다."),
    INVALID_FOLLOW_REQUEST(400, "팔로우 요청이 올바르지 않습니다."),
    INVALID_FEED_REQUEST(400, "피드 조회 조건이 올바르지 않습니다."),
    POST_ACCESS_DENIED(403, "본인의 게시물만 수정·삭제·복구할 수 있습니다."),
    POST_RESTORE_WINDOW_EXPIRED(410, "복구할 수 있는 기간이 지난 게시물입니다."),
    COMMENT_ACCESS_DENIED(403, "본인의 댓글만 수정·삭제할 수 있습니다."),
    NICKNAME_ALREADY_USED(409, "이미 사용 중인 닉네임입니다."),
    ALREADY_REPORTED(409, "이미 신고한 대상입니다."),
    INVALID_BLOCK_REQUEST(400, "차단 요청이 올바르지 않습니다."),
    FOLLOW_BLOCKED_USER(400, "차단한 사용자는 팔로우할 수 없습니다. 차단을 먼저 해제해 주세요."),
    COMMENT_NOT_ALLOWED(403, "이 게시물에는 댓글을 쓸 수 없습니다."),
    INVALID_MEDIA_FILE(400, "업로드한 파일이 올바르지 않습니다."),
    MEDIA_FILE_TOO_LARGE(413, "파일이 너무 큽니다."),
    UNSUPPORTED_MEDIA_FORMAT(415, "지원하지 않는 파일 형식입니다."),
    TRANSIT_ROUTE_NOT_FOUND(422, "장소 사이 대중교통 경로를 찾지 못했습니다."),
    INVALID_SPONTANEOUS_TRIP_REQUEST(400, "Spontaneous trip request is invalid."),
    SPONTANEOUS_START_LOCATION_OUTSIDE_BUSAN(400, "즉흥여행 출발지는 부산광역시 내에서 선택해 주세요."),
    SPONTANEOUS_DESTINATION_NOT_FOUND(404, "Spontaneous trip destination was not found."),
    SPONTANEOUS_DESTINATIONS_NOT_FOUND(404, "No spontaneous trip destinations are available."),
    SPONTANEOUS_COURSE_NOT_FEASIBLE(422, "A feasible spontaneous trip course could not be created."),
    SPONTANEOUS_ROUTE_NOT_FOUND(422, "A route for the spontaneous trip course could not be found."),
    SPONTANEOUS_PROVIDER_ERROR(502, "A downstream spontaneous trip provider returned an error."),
    SPONTANEOUS_PROVIDER_UNAVAILABLE(503, "A downstream spontaneous trip provider is unavailable."),
    FACILITY_TYPE_NOT_SUPPORTED(501, "지원하지 않는 편의시설 유형입니다."),
    EXTERNAL_PROVIDER_UNAVAILABLE(503, "외부 서비스가 응답하지 않습니다."),
    REPORT_NOT_FOUND(404, "신고를 찾을 수 없습니다."),
    CANNOT_SUSPEND_ADMIN(400, "관리자는 정지할 수 없습니다."),
    TOUR_API_QUOTA_EXHAUSTED(429, "오늘 TourAPI 호출 예산을 모두 썼습니다."),
    INVALID_STATS_TYPE(400, "지원하지 않는 통계 유형입니다."),
    INVALID_GOOGLE_TOKEN(401, "구글 로그인 정보를 확인하지 못했습니다."),
    INVALID_TOKEN(401, "인증 정보가 올바르지 않습니다."),
    UNAUTHORIZED(401, "로그인이 필요합니다."),
    TOKEN_EXPIRED(401, "인증이 만료되었습니다. 다시 로그인해 주세요."),
    FORBIDDEN(403, "이 작업을 수행할 권한이 없습니다."),
    SCHEDULE_ACCESS_DENIED(403, "본인의 일정만 조회하거나 수정할 수 있습니다."),
    USER_SUSPENDED(403, "정지된 계정입니다."),
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
