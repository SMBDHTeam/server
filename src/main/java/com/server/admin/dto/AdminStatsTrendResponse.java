package com.server.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 일자별 추이.
 *
 * <p>값이 0인 날도 채워 넣는다. DB 는 행이 없는 날을 돌려주지 않으므로, 그대로 두면
 * 화면이 빈 날을 건너뛰어 그래프가 실제보다 완만해 보인다.
 */
@Schema(description = "일자별 추이. 값이 0인 날도 포함한다.")
public record AdminStatsTrendResponse(
        @Schema(description = "USERS, POSTS, SCHEDULES", example = "POSTS") String metric,
        List<Point> points
) {

    @Schema(description = "하루치")
    public record Point(
            @Schema(example = "2026-08-25") LocalDate date,
            @Schema(example = "7") long count
    ) {
    }
}
