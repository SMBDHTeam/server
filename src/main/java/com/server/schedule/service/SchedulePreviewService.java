package com.server.schedule.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.external.schedule.FastApiScheduleClient;
import com.server.schedule.dto.SchedulePreviewCreateRequest;
import com.server.schedule.dto.SchedulePreviewResponse;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulePreviewService {

    private FastApiScheduleClient fastApiScheduleClient;

    @Autowired(required = false)
    void setFastApiScheduleClient(FastApiScheduleClient fastApiScheduleClient) {
        this.fastApiScheduleClient = fastApiScheduleClient;
    }

    @Transactional
    public SchedulePreviewResponse create(SchedulePreviewCreateRequest request) {
        return requireFastApiScheduleClient().createPreview(request);
    }

    @Transactional(noRollbackFor = BusinessException.class)
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
