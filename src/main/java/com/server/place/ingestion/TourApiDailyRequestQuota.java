package com.server.place.ingestion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TourApiDailyRequestQuota {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final JdbcTemplate jdbcTemplate;

    public TourApiDailyRequestQuota(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean tryReserve(int requests, int dailyLimit) {
        if (requests <= 0 || requests > dailyLimit) {
            return false;
        }
        LocalDate usageDate = LocalDate.now(KOREA_ZONE);
        LocalDateTime now = LocalDateTime.now(KOREA_ZONE);
        int updated = jdbcTemplate.update("""
                INSERT INTO tour_api_request_usage (usage_date, requests_used, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT (usage_date) DO UPDATE
                SET requests_used = tour_api_request_usage.requests_used + EXCLUDED.requests_used,
                    updated_at = EXCLUDED.updated_at
                WHERE tour_api_request_usage.requests_used + EXCLUDED.requests_used <= ?
                """, usageDate, requests, now, dailyLimit);
        return updated == 1;
    }
}
