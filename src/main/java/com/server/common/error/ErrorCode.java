package com.server.common.error;

import org.springframework.http.HttpStatusCode;

public enum ErrorCode {

    INVALID_SCHEDULE_CONDITION(400, "일정 조건이 올바르지 않습니다."),
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
