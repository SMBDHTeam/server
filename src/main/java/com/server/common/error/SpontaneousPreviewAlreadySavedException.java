package com.server.common.error;

import java.util.UUID;

public class SpontaneousPreviewAlreadySavedException extends BusinessException {

    private final UUID scheduleId;

    public SpontaneousPreviewAlreadySavedException(UUID scheduleId, Throwable cause) {
        super(ErrorCode.SPONTANEOUS_PREVIEW_ALREADY_SAVED, cause);
        this.scheduleId = scheduleId;
    }

    public UUID getScheduleId() {
        return scheduleId;
    }
}
