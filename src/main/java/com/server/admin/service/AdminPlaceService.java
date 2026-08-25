package com.server.admin.service;

import com.server.admin.dto.AdminIngestionStatusResponse;
import com.server.admin.dto.AdminPlaceResponse;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.place.domain.Place;
import com.server.place.ingestion.TourApiPlaceIngestionProperties;
import com.server.place.ingestion.TourApiPlaceIngestionResult;
import com.server.place.ingestion.TourApiPlaceIngestionService;
import com.server.place.repository.PlaceRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 장소 데이터 관리.
 *
 * <p>잘못 적재된 장소는 지우지 않고 가린다. 행을 지우면 TourAPI 증분 동기화가 다음 실행에서
 * 같은 장소를 다시 만든다.
 */
@Service
public class AdminPlaceService {

    private static final Logger log = LoggerFactory.getLogger(AdminPlaceService.class);

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final PlaceRepository placeRepository;
    private final TourApiPlaceIngestionService ingestionService;
    private final TourApiPlaceIngestionProperties properties;
    private final JdbcTemplate jdbcTemplate;

    public AdminPlaceService(
            PlaceRepository placeRepository,
            TourApiPlaceIngestionService ingestionService,
            TourApiPlaceIngestionProperties properties,
            JdbcTemplate jdbcTemplate
    ) {
        this.placeRepository = placeRepository;
        this.ingestionService = ingestionService;
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public AdminIngestionStatusResponse getIngestionStatus() {
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        jdbcTemplate.query(
                "select ingestion_status, count(*) as count from places group by ingestion_status "
                        + "order by ingestion_status",
                rs -> {
                    statusCounts.put(rs.getString("ingestion_status"), rs.getLong("count"));
                });

        int used = requestsUsedToday();
        int limit = properties.maxRequestsPerDay();

        return new AdminIngestionStatusResponse(
                statusCounts,
                placeRepository.countByHiddenAtIsNotNull(),
                properties.enabled(),
                properties.enrichmentEnabled(),
                LocalDate.now(KOREA_ZONE),
                used,
                limit,
                Math.max(0, limit - used));
    }

    @Transactional(readOnly = true)
    public List<AdminPlaceResponse> getHiddenPlaces() {
        return placeRepository.findByHiddenAtIsNotNullOrderByHiddenAtDesc().stream()
                .map(AdminPlaceResponse::from)
                .toList();
    }

    /**
     * 수동 적재.
     *
     * <p>스케줄러와 같은 하루 예산을 쓴다. 남은 양이 없으면 시작하지 않는다. 시작해 봐야
     * 예약 단계에서 막혀 아무것도 하지 못하고, 관리자는 왜 안 되는지 알 수 없다.
     *
     * <p>트랜잭션을 걸지 않는다. TourAPI 호출이 길게 이어지므로 DB 커넥션을 그동안 쥐고
     * 있으면 안 된다.
     */
    public TourApiPlaceIngestionResult runIngestion() {
        int remaining = Math.max(0, properties.maxRequestsPerDay() - requestsUsedToday());
        if (remaining <= 0) {
            throw new BusinessException(ErrorCode.TOUR_API_QUOTA_EXHAUSTED);
        }

        log.info("Manual TourAPI ingestion requested. remainingQuota={}", remaining);
        return ingestionService.ingestConfigured();
    }

    @Transactional
    public AdminPlaceResponse updateHidden(Long placeId, boolean hidden, String reason) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));

        if (hidden) {
            place.hide(reason);
            log.info("Place hidden. placeId={}, reason={}", placeId, reason);
        } else {
            place.unhide();
            log.info("Place unhidden. placeId={}", placeId);
        }
        return AdminPlaceResponse.from(place);
    }

    /** 오늘(KST) 쓴 TourAPI 호출 수. 기록이 없으면 0이다. */
    private int requestsUsedToday() {
        Integer used = jdbcTemplate.query(
                "select requests_used from tour_api_request_usage where usage_date = ?",
                rs -> rs.next() ? rs.getInt(1) : 0,
                LocalDate.now(KOREA_ZONE));
        return used == null ? 0 : used;
    }
}
