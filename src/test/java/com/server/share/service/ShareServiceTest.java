package com.server.share.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.schedule.domain.Schedule;
import com.server.schedule.repository.ScheduleRepository;
import com.server.schedule.service.ScheduleService;
import com.server.share.domain.ShareLink;
import com.server.share.dto.ShareLinkCreateRequest;
import com.server.share.repository.ShareLinkRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("공유 서비스")
class ShareServiceTest {

    private final ScheduleRepository scheduleRepository = Mockito.mock(ScheduleRepository.class);
    private final ShareLinkRepository shareLinkRepository = Mockito.mock(ShareLinkRepository.class);
    private final ScheduleService scheduleService = Mockito.mock(ScheduleService.class);
    private final ShareService shareService = new ShareService(
            scheduleRepository, shareLinkRepository, scheduleService);

    @Test
    @DisplayName("365일을 넘는 공유 유효기간을 거부한다")
    void createRejectsExcessiveExpiration() {
        assertThatThrownBy(() -> shareService.create(UUID.randomUUID(), new ShareLinkCreateRequest(366)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_SCHEDULE_CONDITION));
        verify(scheduleRepository, never()).findById(Mockito.any());
    }

    @Test
    @DisplayName("만료된 토큰으로 일정을 조회할 수 없다")
    void getSharedScheduleRejectsExpiredToken() {
        ShareLink expired = new ShareLink(
                schedule(), "stored-hash", LocalDateTime.now().minusMinutes(1));
        when(shareLinkRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> shareService.getSharedSchedule("raw-token"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SHARE_LINK_NOT_FOUND));
        verify(scheduleService, never()).get(Mockito.any());
    }

    @Test
    @DisplayName("존재하지 않는 일정의 공유 링크를 생성할 수 없다")
    void createRejectsMissingSchedule() {
        UUID scheduleId = UUID.randomUUID();
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shareService.create(scheduleId, new ShareLinkCreateRequest(null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    private Schedule schedule() {
        return new Schedule(
                LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-01"),
                LocalTime.parse("09:00"), LocalTime.parse("19:00"),
                "부산역", new BigDecimal("129.04"), new BigDecimal("35.11"),
                "부산역", new BigDecimal("129.04"), new BigDecimal("35.11"),
                "테스트", "{}"
        );
    }
}
