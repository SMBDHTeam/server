package com.server.schedule.repository;

import com.server.schedule.domain.ScheduleCreationRequest;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleCreationRequestRepository extends JpaRepository<ScheduleCreationRequest, UUID> {
    Optional<ScheduleCreationRequest> findByIdempotencyKey(String idempotencyKey);
    Optional<ScheduleCreationRequest> findFirstByPreview_IdAndStatusIn(
            UUID previewId,
            Collection<String> statuses
    );
    long deleteByExpiresAtBefore(OffsetDateTime expiresAt);
}
