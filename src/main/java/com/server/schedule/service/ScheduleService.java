package com.server.schedule.service;

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

/**
 * 일정 생성·조회·수정을 FastAPI에 위임한다.
 *
 * <p>Planner와 실제 경로 계산은 FastAPI가 담당하며 Spring에는 규칙 기반 대체 경로가 없다.
 * {@code app.schedule-fastapi.enabled}가 꺼져 있으면 요청을 처리할 수 없으므로
 * {@link ErrorCode#EXTERNAL_PROVIDER_UNAVAILABLE}를 반환한다.
 *
 * <p>일정 데이터는 FastAPI와 같은 데이터베이스에 저장된다. 따라서 공유 기능처럼
 * 저장된 일정을 직접 읽는 흐름은 JPA 엔티티를 그대로 사용한다.
 */
@Service
public class ScheduleService {

    private FastApiScheduleClient fastApiScheduleClient;

    @Autowired(required = false)
    void setFastApiScheduleClient(FastApiScheduleClient fastApiScheduleClient) {
        this.fastApiScheduleClient = fastApiScheduleClient;
    }

    public ScheduleResponse create(ScheduleCreateRequest request) {
        return requireFastApiScheduleClient().createSchedule(request);
    }

    /**
     * 목록은 축약 응답만 반환한다. 방문지·경로·평가 리포트는 상세 조회에서 제공한다.
     * FastAPI가 아직 요약 엔드포인트를 제공하지 않아 전체 응답을 받아 여기서 줄인다.
     */
    public ScheduleSummaryListResponse getAll() {
        return ScheduleSummaryListResponse.from(requireFastApiScheduleClient().listSchedules());
    }

    public ScheduleResponse get(UUID scheduleId) {
        return requireFastApiScheduleClient().getSchedule(scheduleId);
    }

    public ScheduleResponse update(UUID scheduleId, ScheduleUpdateRequest request) {
        return requireFastApiScheduleClient().updateSchedule(scheduleId, request);
    }

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
