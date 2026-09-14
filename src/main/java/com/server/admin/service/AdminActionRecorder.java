package com.server.admin.service;

import com.server.admin.domain.AdminAction;
import com.server.admin.domain.AdminActionTargetType;
import com.server.admin.domain.AdminActionType;
import com.server.admin.repository.AdminActionRepository;
import org.springframework.stereotype.Component;

/**
 * 관리자 조치를 기록한다.
 *
 * <p><b>조치와 같은 트랜잭션에서 저장한다.</b> 기록을 별도 트랜잭션이나 비동기로 빼면
 * 조치는 됐는데 기록만 빠지는 경우가 생기고, 그러면 남은 기록을 믿을 수 없다.
 * 기록에 실패하면 조치도 함께 되돌아가는 편이 맞다.
 *
 * <p>호출부는 이미 {@code @Transactional} 안이므로 여기서 트랜잭션을 다시 열지 않는다.
 */
@Component
public class AdminActionRecorder {

    private final AdminActionRepository adminActionRepository;

    public AdminActionRecorder(AdminActionRepository adminActionRepository) {
        this.adminActionRepository = adminActionRepository;
    }

    public void record(
            Long adminId,
            AdminActionType action,
            AdminActionTargetType targetType,
            Long targetId,
            String reason,
            String detail
    ) {
        if (adminId == null) {
            // 인가가 걸려 있어 여기까지 오면 관리자가 있다. 그래도 기록할 주체를 모르는
            // 조치를 남기면 이력이 거짓이 되므로, 조용히 넘기지 않고 막는다.
            throw new IllegalStateException("관리자 ID 없이 조치를 기록할 수 없다");
        }
        adminActionRepository.save(
                new AdminAction(adminId, action, targetType, targetId, reason, detail));
    }

    public void record(
            Long adminId, AdminActionType action, AdminActionTargetType targetType, Long targetId) {
        record(adminId, action, targetType, targetId, null, null);
    }
}
