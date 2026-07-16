package com.server.schedule.repository;

import com.server.schedule.domain.SchedulePreview;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SchedulePreviewRepository extends JpaRepository<SchedulePreview, UUID> {
    List<SchedulePreview> findByExpiresAtBeforeAndStatusNot(OffsetDateTime expiresAt, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select preview from SchedulePreview preview where preview.id = :previewId")
    Optional<SchedulePreview> findForUpdateById(@Param("previewId") UUID previewId);
}
