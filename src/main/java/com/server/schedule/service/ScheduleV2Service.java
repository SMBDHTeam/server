package com.server.schedule.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.external.schedule.FastApiScheduleClient;
import com.server.schedule.dto.SchedulePreviewScheduleRequest;
import com.server.schedule.dto.ScheduleResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ScheduleV2Service {

    private FastApiScheduleClient fastApiScheduleClient;

    @Autowired(required = false)
    void setFastApiScheduleClient(FastApiScheduleClient fastApiScheduleClient) {
        this.fastApiScheduleClient = fastApiScheduleClient;
    }

    public ScheduleResponse create(
            SchedulePreviewScheduleRequest request,
            String idempotencyKey
    ) {
        validateKey(idempotencyKey);
        return requireFastApiScheduleClient().createScheduleFromPreview(request, idempotencyKey);
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
    }

    private FastApiScheduleClient requireFastApiScheduleClient() {
        if (fastApiScheduleClient == null || !fastApiScheduleClient.enabled()) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
        }
        return fastApiScheduleClient;
    }
}
