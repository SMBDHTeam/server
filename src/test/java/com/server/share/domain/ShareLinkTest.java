package com.server.share.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.schedule.domain.Schedule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("공유 링크")
class ShareLinkTest {

    @Test
    @DisplayName("만료되거나 폐기된 링크는 사용할 수 없다")
    void expiredOrRevokedLinkIsUnavailable() {
        LocalDateTime now = LocalDateTime.now();
        ShareLink expired = new ShareLink(schedule(), "hash-a", now.minusMinutes(1));
        ShareLink revoked = new ShareLink(schedule(), "hash-b", now.plusDays(1));
        revoked.revoke(now);

        assertThat(expired.isAvailable(now)).isFalse();
        assertThat(revoked.isAvailable(now)).isFalse();
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
