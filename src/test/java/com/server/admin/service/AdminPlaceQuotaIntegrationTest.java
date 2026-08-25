package com.server.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * TourAPI 예산 확인.
 *
 * <p>{@code tour_api_request_usage} 는 Flyway 만 만드는 테이블이라 JPA 스키마를 쓰는 H2
 * 테스트에는 존재하지 않는다. 실제 PostgreSQL 에 전체 migration 을 적용해 확인한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///tour_quota_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
@DisplayName("TourAPI 예산")
class AdminPlaceQuotaIntegrationTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    @Autowired
    private AdminPlaceService adminPlaceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private void useQuota(int requests) {
        jdbcTemplate.update("""
                INSERT INTO tour_api_request_usage (usage_date, requests_used, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT (usage_date) DO UPDATE SET requests_used = EXCLUDED.requests_used
                """, LocalDate.now(KOREA_ZONE), requests, LocalDateTime.now());
    }

    @Test
    @DisplayName("남은 예산을 한도에서 사용량을 뺀 값으로 준다")
    void reportsRemainingQuota() {
        useQuota(100);

        var status = adminPlaceService.getIngestionStatus();

        assertThat(status.requestsUsed()).isEqualTo(100);
        assertThat(status.requestsRemaining()).isEqualTo(status.dailyLimit() - 100);
        assertThat(status.quotaDate()).isEqualTo(LocalDate.now(KOREA_ZONE));
    }

    @Test
    @DisplayName("예산을 다 쓰면 수동 적재를 시작하지 않는다")
    void refusesIngestionWhenQuotaExhausted() {
        // 시작해 봐야 예약 단계에서 막혀 아무것도 하지 못하고, 관리자는 왜 안 되는지 알 수 없다.
        useQuota(adminPlaceService.getIngestionStatus().dailyLimit());

        assertThatThrownBy(() -> adminPlaceService.runIngestion())
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TOUR_API_QUOTA_EXHAUSTED);
    }
}
