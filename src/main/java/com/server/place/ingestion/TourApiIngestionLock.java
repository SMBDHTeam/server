package com.server.place.ingestion;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TourApiIngestionLock {

    private static final String LOCK_NAME = "tour-api-place-ingestion";
    private static final Duration LOCK_DURATION = Duration.ofHours(6);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final JdbcTemplate jdbcTemplate;

    public TourApiIngestionLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Lease> tryAcquire() {
        String owner = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(KOREA_ZONE);
        int updated = jdbcTemplate.update("""
                INSERT INTO place_ingestion_locks (lock_name, locked_by, locked_until)
                VALUES (?, ?, ?)
                ON CONFLICT (lock_name) DO UPDATE
                SET locked_by = EXCLUDED.locked_by,
                    locked_until = EXCLUDED.locked_until
                WHERE place_ingestion_locks.locked_until <= ?
                """, LOCK_NAME, owner, now.plus(LOCK_DURATION), now);
        return updated == 1 ? Optional.of(new Lease(owner)) : Optional.empty();
    }

    public void release(Lease lease) {
        jdbcTemplate.update(
                "DELETE FROM place_ingestion_locks WHERE lock_name = ? AND locked_by = ?",
                LOCK_NAME,
                lease.owner()
        );
    }

    public record Lease(String owner) {
    }
}
