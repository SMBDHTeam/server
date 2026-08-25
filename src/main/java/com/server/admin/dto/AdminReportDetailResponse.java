package com.server.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 신고 상세. 신고 내용만으로는 판단할 수 없어 대상 원본을 함께 준다.
 *
 * <p>대상이 이미 지워졌으면 {@code target} 이 {@code null} 이다. 신고가 접수된 뒤 작성자가
 * 스스로 지웠거나 다른 관리자가 먼저 조치한 경우다. 이때는 더 볼 것이 없으므로 신고를
 * 종결하면 된다.
 */
@Schema(description = "신고 상세와 대상 원본")
public record AdminReportDetailResponse(
        AdminReportResponse report,
        @Schema(description = "신고 대상 원본. 이미 삭제됐으면 null")
        Target target
) {

    @Schema(description = "신고 대상 원본")
    public record Target(
            @Schema(example = "7") Long id,
            @Schema(description = "작성자 또는 대상 사용자") AdminReportResponse.Reporter author,
            @Schema(description = "게시물·댓글이면 본문, 사용자면 닉네임", example = "광안리 야경 보러 갔어요")
            String content,
            @Schema(description = "이미 삭제된 대상인지", example = "false") boolean deleted
    ) {
    }
}
