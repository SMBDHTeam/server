package com.server.common.error;

import org.springframework.http.HttpStatusCode;

public enum ErrorCode {

    INVALID_SCHEDULE_CONDITION(400, "일정 조건이 올바르지 않습니다."),
    INVALID_SCHEDULE_PREVIEW_REQUEST(400, "일정 미리보기 요청이 올바르지 않습니다."),
    FIXED_BASE_LOCATION_REQUIRED(400, "고정 숙소 위치가 필요합니다."),
    PER_NIGHT_LOCATION_MISSING(400, "숙박일별 숙소 위치가 필요합니다."),
    MUST_VISIT_PLACE_LIMIT_EXCEEDED(400, "필수 방문 장소 수가 허용 범위를 초과했습니다."),
    INVALID_EXTERNAL_PLACE(400, "외부 장소 정보가 올바르지 않습니다."),
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
    TRANSIT_ROUTE_NOT_FOUND(422, "장소 사이 대중교통 경로를 찾지 못했습니다."),
    FACILITY_TYPE_NOT_SUPPORTED(501, "지원하지 않는 편의시설 유형입니다."),
    EXTERNAL_PROVIDER_UNAVAILABLE(503, "외부 서비스가 응답하지 않습니다.");

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
