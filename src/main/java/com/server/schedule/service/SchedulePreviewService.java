package com.server.schedule.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.external.schedule.FastApiScheduleClient;
import com.server.schedule.dto.SchedulePreviewCreateRequest;
import com.server.schedule.dto.SchedulePreviewResponse;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Preview 생성·조회를 FastAPI에 위임한다.
 *
 * <p>입력 정규화, 일차별 가용시간 계산, 충돌 검사는 모두 FastAPI가 수행한다.
 * Preview는 FastAPI와 공유하는 {@code schedule_previews} 테이블에 저장되므로
 * 만료 Preview 정리는 {@link ScheduleGenerationCleanupService}가 계속 담당한다.
 */
@Service
public class SchedulePreviewService {

    private FastApiScheduleClient fastApiScheduleClient;

    @Autowired(required = false)
    void setFastApiScheduleClient(FastApiScheduleClient fastApiScheduleClient) {
        this.fastApiScheduleClient = fastApiScheduleClient;
    }

    public SchedulePreviewResponse create(SchedulePreviewCreateRequest request) {
        return requireFastApiScheduleClient().createPreview(request);
    }

    public SchedulePreviewResponse get(UUID previewId) {
        return requireFastApiScheduleClient().getPreview(previewId);
    }

    private FastApiScheduleClient requireFastApiScheduleClient() {
        if (fastApiScheduleClient == null || !fastApiScheduleClient.enabled()) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
        }
        return fastApiScheduleClient;
    }
}
