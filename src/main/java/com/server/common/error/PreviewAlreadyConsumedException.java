package com.server.common.error;

import java.util.UUID;

public class PreviewAlreadyConsumedException extends BusinessException {

    private final UUID scheduleId;

    public PreviewAlreadyConsumedException(UUID scheduleId) {
        super(ErrorCode.PREVIEW_ALREADY_CONSUMED);
        this.scheduleId = scheduleId;
    }

    public UUID getScheduleId() {
        return scheduleId;
    }
}
