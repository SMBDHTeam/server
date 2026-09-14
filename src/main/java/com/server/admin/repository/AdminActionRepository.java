package com.server.admin.repository;

import com.server.admin.domain.AdminAction;
import com.server.admin.domain.AdminActionTargetType;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminActionRepository extends JpaRepository<AdminAction, Long> {

    List<AdminAction> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    List<AdminAction> findByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(
            AdminActionTargetType targetType, Long targetId, Pageable pageable);

    long countByTargetTypeAndTargetId(AdminActionTargetType targetType, Long targetId);

    List<AdminAction> findByTargetTypeOrderByCreatedAtDescIdDesc(
            AdminActionTargetType targetType, Pageable pageable);

    long countByTargetType(AdminActionTargetType targetType);
}
