package com.server.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.place.repository.PlaceRepository;
import com.server.question.repository.QuestionRepository;
import com.server.schedule.domain.SchedulePreview;
import com.server.schedule.repository.SchedulePreviewRepository;
import com.server.schedule.repository.ScheduleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("일정 Preview 서비스")
class SchedulePreviewServiceTest {

    @Test
    @DisplayName("만료된 Preview 조회는 상태를 만료로 바꾸고 410 오류를 반환한다")
    void expiredPreviewReturnsGone() {
        SchedulePreviewRepository previewRepository = mock(SchedulePreviewRepository.class);
        ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
        SchedulePreview preview = new SchedulePreview(
                "READY", LocalDate.parse("2026-07-16"), LocalDate.parse("2026-07-16"),
                "Asia/Seoul", "UNDECIDED", "ATTRACTION_ROUTES_ONLY", "{}", "[]", null,
                "[]", "{\"preferences\":[],\"unrecognizedTexts\":[]}", "[]", "[]",
                OffsetDateTime.parse("2026-07-15T09:30:00+09:00"),
                OffsetDateTime.parse("2026-07-15T09:00:00+09:00"));
        UUID previewId = preview.getId();
        when(previewRepository.findById(previewId)).thenReturn(Optional.of(preview));
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        SchedulePreviewService service = new SchedulePreviewService(
                previewRepository, scheduleRepository, mock(QuestionRepository.class),
                mock(PlaceRepository.class), JsonMapper.builder().findAndAddModules().build(), clock);

        assertThatThrownBy(() -> service.get(previewId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PREVIEW_EXPIRED));
        assertThat(preview.getStatus()).isEqualTo("EXPIRED");
    }
}
