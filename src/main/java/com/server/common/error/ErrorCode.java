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
    POST_DELETED_BY_ADMIN(403, "관리자가 삭제한 게시물은 복구할 수 없습니다."),
    COMMENT_ACCESS_DENIED(403, "본인의 댓글만 수정·삭제할 수 있습니다."),
    NICKNAME_ALREADY_USED(409, "이미 사용 중인 닉네임입니다."),
    ALREADY_REPORTED(409, "이미 신고한 대상입니다."),
    CANNOT_REPORT_OWN_TARGET(400, "본인의 게시물·댓글이나 자기 자신은 신고할 수 없습니다."),
    INVALID_BLOCK_REQUEST(400, "차단 요청이 올바르지 않습니다."),
    FOLLOW_BLOCKED_USER(400, "차단한 사용자는 팔로우할 수 없습니다. 차단을 먼저 해제해 주세요."),
    COMMENT_NOT_ALLOWED(403, "이 게시물에는 댓글을 쓸 수 없습니다."),
    INVALID_MEDIA_FILE(400, "업로드한 파일이 올바르지 않습니다."),
    MEDIA_FILE_TOO_LARGE(413, "파일이 너무 큽니다."),
    UNSUPPORTED_MEDIA_FORMAT(415, "지원하지 않는 파일 형식입니다."),
    TRANSIT_ROUTE_NOT_FOUND(422, "장소 사이 대중교통 경로를 찾지 못했습니다."),
    INVALID_SPONTANEOUS_TRIP_REQUEST(400, "즉흥여행 요청 조건이 올바르지 않습니다."),
    SPONTANEOUS_START_LOCATION_OUTSIDE_BUSAN(400, "즉흥여행 출발지는 부산광역시 내에서 선택해 주세요."),
    SPONTANEOUS_DESTINATION_NOT_FOUND(404, "선택한 즉흥여행 목적지를 찾을 수 없습니다."),
    SPONTANEOUS_DESTINATIONS_NOT_FOUND(404, "현재 조건에 맞는 즉흥여행 목적지를 찾을 수 없습니다. 여행 시간이나 테마를 변경해 주세요."),
    SPONTANEOUS_DESTINATION_ROUTE_NOT_FOUND(404, "선택한 시간과 이동수단으로 왕복 가능한 경로가 없습니다."),
    SPONTANEOUS_DESTINATION_TIME_TOO_SHORT(404, "왕복 이동시간과 최소 체류시간이 부족합니다. 복귀 시간을 늦춰주세요."),
    SPONTANEOUS_DESTINATION_TRANSPORT_CONSTRAINT(404, "선택한 이동수단과 여행 시간으로 왕복 가능한 목적지가 없습니다. 시간이나 이동수단을 변경해주세요."),
    SPONTANEOUS_DESTINATION_CANDIDATES_NOT_FOUND(404, "선택한 테마에 맞는 추천 목적지가 없습니다. 테마를 변경하거나 선택을 줄여주세요."),
    SPONTANEOUS_COURSE_NOT_FEASIBLE(422, "선택한 조건으로 가능한 즉흥여행 코스를 만들 수 없습니다. 여행 시간이나 테마를 변경해 주세요."),
    SPONTANEOUS_COURSE_RETURN_TIME_EXCEEDED(422, "선택한 이동수단으로는 설정한 복귀 시간 안에 돌아오기 어렵습니다. 이동수단을 변경하거나 더 가까운 목적지를 선택해 주세요."),
    SPONTANEOUS_COURSE_THEME_NOT_FEASIBLE(422, "선택한 지역에서는 요청한 테마를 모두 포함한 코스를 만들기 어렵습니다. 테마를 줄이거나 다른 목적지를 선택해 주세요."),
    SPONTANEOUS_COURSE_PLACES_CLOSED(422, "방문 예정 시간에 이용 가능한 장소가 부족합니다. 출발 시간을 변경하거나 다른 목적지를 선택해 주세요."),
    SPONTANEOUS_ROUTE_NOT_FOUND(422, "선택한 조건으로 이동 가능한 경로를 찾을 수 없습니다."),
    SPONTANEOUS_PREVIEW_INVALID(400, "즉흥여행 미리보기가 변조되었거나 올바르지 않습니다."),
    SPONTANEOUS_PREVIEW_OWNER_MISMATCH(403, "본인이 만든 즉흥여행 미리보기만 저장할 수 있습니다."),
    SPONTANEOUS_PREVIEW_EXPIRED(410, "즉흥여행 미리보기가 만료되었습니다. 코스를 다시 만들어 주세요."),
    SPONTANEOUS_PREVIEW_ALREADY_SAVED(409, "이미 저장된 즉흥여행 미리보기입니다."),
    SPONTANEOUS_PLACE_HIDDEN(422, "현재 저장할 수 없는 방문지가 코스에 포함되어 있습니다."),
    SPONTANEOUS_RETURN_TIME_EXCEEDED(422, "귀환 제한 시간을 넘겨 변경 내용을 저장할 수 없습니다."),
    SPONTANEOUS_PROVIDER_ERROR(502, "여행 정보 제공 서비스 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),
    SPONTANEOUS_PROVIDER_UNAVAILABLE(503, "여행 정보 제공 서비스를 현재 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    FACILITY_TYPE_NOT_SUPPORTED(501, "지원하지 않는 편의시설 유형입니다."),
    EXTERNAL_PROVIDER_UNAVAILABLE(503, "외부 서비스가 응답하지 않습니다."),
    REPORT_NOT_FOUND(404, "신고를 찾을 수 없습니다."),
    INVALID_REPORT_REQUEST(400, "신고 요청이 올바르지 않습니다."),
    CANNOT_SUSPEND_ADMIN(400, "관리자는 정지할 수 없습니다."),
    TOUR_API_QUOTA_EXHAUSTED(429, "오늘 TourAPI 호출 예산을 모두 썼습니다."),
    INVALID_STATS_TYPE(400, "지원하지 않는 통계 유형입니다."),
    INVALID_ADMIN_REQUEST(400, "관리자 요청이 올바르지 않습니다."),
    CANNOT_DEMOTE_LAST_ADMIN(409, "마지막 관리자는 일반 사용자로 바꿀 수 없습니다."),
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
