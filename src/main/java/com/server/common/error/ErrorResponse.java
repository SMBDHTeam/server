package com.server.common.error;

import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "모든 실패 응답이 공통으로 쓰는 형태. 클라이언트는 HTTP 상태가 아니라 code로 분기해도 된다.")
public record ErrorResponse(
        @Schema(description = "오류 코드. 상태별 가능한 값은 각 오퍼레이션의 응답 설명에 있다.",
                example = "INVALID_PLACE_SEARCH_REQUEST")
        String code,
        @Schema(description = "사용자에게 보여줄 수 있는 한국어 메시지",
                example = "장소 검색 조건이 올바르지 않습니다.")
        String message,
        @Schema(description = "원인을 필드 단위로 좁힐 수 있을 때 채운다. 좁힐 수 없으면 빈 배열이다.")
        List<FieldErrorResponse> fieldErrors,
        @Schema(description = "요청 추적 ID. 응답 헤더 X-Trace-Id와 같은 값이며 서버 로그와 연결된다.",
                example = "0ebd1950-190c-444a-aa15-bf6b533686e7")
        String traceId,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID scheduleId
) {

    public static ErrorResponse of(ErrorCode errorCode, String traceId) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), List.of(), traceId, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldErrorResponse> fieldErrors, String traceId) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), fieldErrors, traceId, null);
    }

    public static ErrorResponse consumed(ErrorCode errorCode, String traceId, UUID scheduleId) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), List.of(), traceId, scheduleId);
    }

    public record FieldErrorResponse(
            @Schema(description = "문제가 된 위치. 요청 본문은 JSON 경로, 그 외는 파라미터·헤더 이름",
                    example = "size")
            String field,
            @Schema(description = "사유", example = "1 이상 50 이하여야 합니다. 요청 값: 1000")
            String message
    ) {
    }
}
