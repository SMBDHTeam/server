package com.server.schedule.service;

import com.server.auth.web.CurrentUser;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.external.schedule.FastApiScheduleClient;
import com.server.schedule.dto.ScheduleCreateRequest;
import com.server.schedule.dto.ScheduleMapResponse;
import com.server.schedule.dto.ScheduleResponse;
import com.server.schedule.dto.ScheduleSummaryListResponse;
import com.server.schedule.dto.ScheduleUpdateRequest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleService {

    private FastApiScheduleClient fastApiScheduleClient;

    @Autowired(required = false)
    void setFastApiScheduleClient(FastApiScheduleClient fastApiScheduleClient) {
        this.fastApiScheduleClient = fastApiScheduleClient;
    }

    public ScheduleResponse create(ScheduleCreateRequest request) {
        return requireFastApiScheduleClient().createSchedule(request, CurrentUser.idOrNull());
    }

    /**
     * 목록은 축약 응답만 반환한다. 방문지·경로·평가 리포트는 상세 조회에서 제공한다.
     * FastAPI가 아직 요약 엔드포인트를 제공하지 않아 전체 응답을 받아 여기서 줄인다.
     */
    @Transactional(readOnly = true)
    public ScheduleSummaryListResponse getAll() {
        // 로그인한 사용자의 일정만 반환한다. 인가를 아직 켜지 않아 비로그인 호출도 들어오는데,
        // 전체를 돌려주면 남의 일정이 그대로 노출된다. 이때는 빈 목록을 준다.
        return ScheduleSummaryListResponse.from(
                requireFastApiScheduleClient().listSchedules(CurrentUser.idOrNull()));
    }

    @Transactional(readOnly = true)
    public ScheduleResponse get(UUID scheduleId) {
        return requireFastApiScheduleClient().getSchedule(scheduleId);
    }

    public ScheduleResponse update(UUID scheduleId, ScheduleUpdateRequest request) {
        return requireFastApiScheduleClient().updateSchedule(scheduleId, request);
    }

    @Transactional(readOnly = true)
    public ScheduleMapResponse getMap(UUID scheduleId, Integer dayNo) {
        return requireFastApiScheduleClient().getScheduleMap(scheduleId, dayNo);
    }

    private FastApiScheduleClient requireFastApiScheduleClient() {
        if (fastApiScheduleClient == null || !fastApiScheduleClient.enabled()) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
        }
        return fastApiScheduleClient;
    }
}
